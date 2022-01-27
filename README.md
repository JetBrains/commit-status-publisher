# Teamcity Commit Status Publisher
[![official JetBrains project](http://jb.gg/badges/official-plastic.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)

TeamCity [build feature](https://www.jetbrains.com/help/teamcity/?Adding+Build+Features) publishing a commit status to an external
system like JetBrains Upsource, GitHub, Gerrit Code Review tool, Bitbucket Cloud or
Atlassian Stash.

The plugin is compatible with TeamCity 7.1.x and later and **[is bundled] (see [Commit Status Publisher](https://www.jetbrains.com/help/teamcity/?Commit+Status+Publisher)) since TeamCity 10.x**.

## Download
Get plugin from the latest build corresponding to your TeamCity version:

| TeamCity | Status | Download |
|----------|--------|----------|
| TeamCity 10.0.x | [![](http://teamcity.jetbrains.com/app/rest/builds/buildType:TeamCityPluginsByJetBrains_CommitStatusPublisher_TeamCity100x/statusIcon.svg)](https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamCityPluginsByJetBrains_CommitStatusPublisher_TeamCity100x) | [Download](https://teamcity.jetbrains.com/repository/download/TeamCityPluginsByJetBrains_CommitStatusPublisher_TeamCity100x/.lastSuccessful/commit-status-publisher.zip?guest=1) |
| TeamCity 9.1.x | [![](http://teamcity.jetbrains.com/app/rest/builds/buildType:TeamCityPluginsByJetBrains_CommitStatusPublisher_91/statusIcon.svg)](https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamCityPluginsByJetBrains_CommitStatusPublisher_91) | [Download](https://teamcity.jetbrains.com/repository/download/TeamCityPluginsByJetBrains_CommitStatusPublisher_91/.lastSuccessful/commit-status-publisher.zip?guest=1) |

Found a bug? File [an issue](https://youtrack.jetbrains.com/newIssue?project=TW&clearDraft=true&c=Assignee+neverov&c=Subsystem+plugins%3A+other&c=tag+plugin_statusPublisher).

## Build
### Local plugin build
In the absence of the TeamCity project, you can build the plugin locally using libraries from the TeamCity distribution by running the following command:
```
gradle clean build -PTeamCityTestLibs=TEAMCITY_DISTR/devPackage/tests -PTeamCityLibs=TEAMCITY_DISTR/webapps/ROOT/WEB-INF/lib -PTeamCityVersion=DIST_VERSION
```
Where TEAMCITY_DISTR must be replaced with an absolute path to the TeamCity distribution directory. And DIST_VERSION must be replaced with actual version of distribution.

### Local plugin build from the scratch
For this build you require full bundle of TeamCity sources. Before building the plugin locally, you should build required artifacts using IntelliJ IDEA. To do it open in
IntelliJ IDEA `Build | Build artifacts` menu and build artifacts: `dist-openapi-integration` and `web-deployment`.

To build the plugin locally if it is located within a subdirectory run the command in the plugin project root directory:
```
gradle clean build -PTeamCityVersion=TEAMCITY_VERSION
```
Parameter TEAMCITY_VERSION is optional (default value: SNAPSHOT), it describes which version of TeamCity libraries should be used from remote maven repository (or local cache).

### Local plugin build using separate TeamCity sources
If TeamCity project sources are located elsewhere, not as plugin parent directory, please use the following command:
```
gradle clean build -PTeamCityTestLibs=TEAMCITY_PROJECT/.idea_artifacts/dist_openapi_integration/tests -PTeamCityLibs=TEAMCITY_PROJECT/.idea_artifacts/web-deployment/WEB-INF/lib -PTeamCityVersion=DIST_VERSION
```
Where TEAMCITY_PROJECT must be replaced with an absolute path to the TeamCity project directory. DIST_VERSION is optional, by default will be used SNAPSHOT value
and will be used prebuilt version of TeamCity Open API from local maven cache, or you can pass actual version of TeamCity to get Open API from remote maven repository.

### Build result

The target directory of the project root will contain the
`commit-status-publisher.zip` file, which is ready [to be installed](https://www.jetbrains.com/help/teamcity/?Installing+Additional+Plugins).

Once the plugin is installed, add the Commit Status Publisher  [build feature](https://www.jetbrains.com/help/teamcity/?Adding+Build+Features) to your build configuration.

## Publishing TeamCity Open API to local maven cache
If you have TeamCity sources, and you want to use locally built version of TeamCity Open API you can prebuild it with IntelliJ IDEA, using menu `Build | Build artifacts`
and selecting there artifact `dist-openapi`. After successfull build you should execute command to publish built artefact to maven cache:
```
mvn -f install_teamcity_api_pom.xml package -DTeamCityVersion=DIST_VERSION
```
Where DIST_VERSION must be replaced by SNAPSHOT by default, or any version, that you want than to pass as TeamCityVersion parametr to the gradle script.
