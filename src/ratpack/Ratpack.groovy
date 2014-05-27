import io.smaldini.github.GithubModule
import io.smaldini.github.GithubService
import ratpack.groovy.templating.TemplatingModule
import ratpack.jackson.JacksonModule
import reactor.core.composable.Stream
import reactor.tuple.Tuple2

import static ratpack.groovy.Groovy.groovyTemplate
import static ratpack.groovy.Groovy.ratpack
import static ratpack.jackson.Jackson.json

ratpack {

	modules {
		register new JacksonModule()
		register new GithubModule()
		get(TemplatingModule).staticallyCompile = true
	}

	handlers {
		assets "public"

		/**
		 * Homepage
		 */
		get {
			render groovyTemplate("index.html", title: "API home")
		}

		/**
		 * Capture GET localhost:port/orgs/[org]/rank where [org] is an existing Github Org
		 */
		get("orgs/:org/rank") { GithubService githubService ->

			//fetch github Repos asynchronously
			githubService.findRepositoriesByOrganizationId((String)allPathTokens['org']).
					//bind a promise for pull requests size per repo
							mapMany { String repo ->
								githubService.countPullRequestsByRepository(repo).map { Integer pr ->

									//just a bit of noise to be sure of what's happening for the cautious reader :)
									println "repo $repo pr $pr"

									//Tuple is a flat structure, good we don't need more for a pair of repo/prs
									Tuple2.of(repo, pr)
								}
							}.
							collect(20).
					// sort the previously collected repo/pr pair list per PR (t2) and Repo name (t1)
							map { List<Tuple2<String, Integer>> tupleList ->
								println "tupleList size ${tupleList.size()}"
								tupleList.sort { Tuple2<String, Integer> e1, Tuple2<String, Integer> e2 ->
									e2.t2 == e1.t2 ? e1.t1 <=> e2.t1 : e2.t2 <=> e1.t2
								}
							}.

					//slice the first 5 elements
							map { List<Tuple2<String, Integer>> tupleList ->
								tupleList.size() > 5 ? tupleList[0..5] : tupleList
							}.


					//render the list as json, using a "map[repo-name] = pr" structure for Jackson marshalling
							consume { Iterable<Tuple2<String, Integer>> tupleList ->
								tupleList.each { Tuple2<String, Integer> tuple ->
									println "consumed $tuple.t1 = $tuple.t2"
								}
								//render json(tupleList.inject([:]) { map, Tuple2<String, Integer> tuple -> map[tuple.t1] = tuple.t2; map })
							}.

					//catch any errors and return them to the http response
							when(Exception) { Exception ex ->
								ex.printStackTrace()
								//response.status(503)
								//render json(ex.message)
							}//.flush()

			render 'Hello'

		}

	}

}


