#Teamcity Commit Status Publisher


TeamCity [build feature](https://confluence.jetbrains.com/display/TCDL/Adding+Build+Features) publishing a commit status to an external
system like JetBrains Upsource, GitHub, Gerrit Code Review tool, Bitbucket Cloud or
Atlassian Stash.

The plugin is compatible with TeamCity 7.1.x and later and **[is bundled] (https://confluence.jetbrains.com/display/TCD10/Commit+Status+Publisher) since TeamCity 10.x**

## Download
Get plugin from the latest build corresponding to your TeamCity version (download commit-status-publisher.zip from the latest successful build's artifacts):
- [TeamCity 10 ![](http://teamcity.jetbrains.com/app/rest/builds/buildType:TeamCityPluginsByJetBrains_CommitStatusPublisher_Master/statusIcon)] (https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamCityPluginsByJetBrains_CommitStatusPublisher_Master)
- [TeamCity 8.0.x-9.1.x ![](http://teamcity.jetbrains.com/app/rest/builds/buildType:TeamCityPluginsByJetBrains_CommitStatusPublisher_91/statusIcon)](https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamCityPluginsByJetBrains_CommitStatusPublisher_91) 
- [TeamCity 7.1.x ![](http://teamcity.jetbrains.com/app/rest/builds/buildType:TeamCityPluginsByJetBrains_Unsorted_CommitStatusPublisher71/statusIcon)](http://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamCityPluginsByJetBrains_CommitStatusPublisher_71) 

Found a bug? File [an issue](https://youtrack.jetbrains.com/newIssue?project=TW&clearDraft=true&c=Assignee+neverov&c=Subsystem+plugins%3A+other&c=tag+plugin_statusPublisher).

## Local plugin build

To build the plugin locally, run the
```
mvn package
```
command in the root directory.

The target directory of the project root will contain the
commit-status-publisher.zip file, which is ready [to be installed]
(https://confluence.jetbrains.com/display/TCDL/Installing+Additional+Plugins).

Once the plugin is installed, add the Commit Status Publisher  [build feature](https://confluence.jetbrains.com/display/TCDL/Adding+Build+Features) to your build configuration.
