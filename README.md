#Teamcity Commit Status Publisher

TeamCity build feature publishing a commit status to an external
system like GitHub, Gerrit Code Review tool, Bitbucket Cloud or
Atlassian Stash.

## Builds

- [TeamCity 10] (https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamCityPluginsByJetBrains_CommitStatusPublisher_Master) ![](http://teamcity.jetbrains.com/app/rest/builds/buildType:TeamCityPluginsByJetBrains_CommitStatusPublisher_Master/statusIcon)
- [TeamCity 8.0.x-9.1.x](https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamCityPluginsByJetBrains_CommitStatusPublisher_91) ![](http://teamcity.jetbrains.com/app/rest/builds/buildType:TeamCityPluginsByJetBrains_CommitStatusPublisher_91/statusIcon)
- [TeamCity 7.1.x](http://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamCityPluginsByJetBrains_CommitStatusPublisher_71) ![](http://teamcity.jetbrains.com/app/rest/builds/buildType:TeamCityPluginsByJetBrains_Unsorted_CommitStatusPublisher71/statusIcon)

Found a bug? File [an issue](https://youtrack.jetbrains.com/newIssue?project=TW&clearDraft=true&c=Assignee+neverov&c=Subsystem+plugins%3A+other&c=tag+plugin_statusPublisher).

## Local plugin build

To build the plugin locally run the
```
mvn package
```
command in the root directory.

The target directory of the project root will contain the
commit-status-publisher.zip file, which is ready [to be installed]
(https://confluence.jetbrains.com/display/TCDL/Installing+Additional+Plugins).
