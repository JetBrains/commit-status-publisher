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
| TeamCity 7.1.x | [![](http://teamcity.jetbrains.com/app/rest/builds/buildType:TeamCityPluginsByJetBrains_Unsorted_CommitStatusPublisher71/statusIcon.svg)](http://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamCityPluginsByJetBrains_CommitStatusPublisher_71) | [Download](https://teamcity.jetbrains.com/repository/download/TeamCityPluginsByJetBrains_CommitStatusPublisher_71/.lastSuccessful/commit-status-publisher.zip?guest=1) |

Found a bug? File [an issue](https://youtrack.jetbrains.com/newIssue?project=TW&clearDraft=true&c=Assignee+neverov&c=Subsystem+plugins%3A+other&c=tag+plugin_statusPublisher).

## Local plugin build

To build the plugin locally if it is located within a subdirectory of TeamCity project, run the
```
mvn package
```
command in the plugin project root directory.

If TeamCity project sources are located elsewhere, please use the following command:
```
mvn package -Dteamcity.path.testlib=TEAMCITY_PROJECT/.idea_artifacts/dist_openapi_integration/tests -Dteamcity.path.lib=TEAMCITY_PROJECT/.idea_artifacts/web-deployment/WEB-INF/lib
```
Where TEAMCITY_PROJECT must be replaced with an absolute path to the TeamCity project directory.

In the absence of the TeamCity project, you can build the plugin locally using libraries from the TeamCity distribution by running the following command:
```
mvn package -Dteamcity.path.testlib=TEAMCITY_DISTR/devPackage/tests -Dteamcity.path.lib=TEAMCITY_DISTR/webapps/ROOT/WEB-INF/lib
```
Where TEAMCITY_DISTR must be replaced with an absolute path to the TeamCity distribution directory.

The target directory of the project root will contain the
`commit-status-publisher.zip` file, which is ready [to be installed](https://www.jetbrains.com/help/teamcity/?Installing+Additional+Plugins).

Once the plugin is installed, add the Commit Status Publisher  [build feature](https://www.jetbrains.com/help/teamcity/?Adding+Build+Features) to your build configuration.
