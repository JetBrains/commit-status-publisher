#T eamcity Commit Status Publisher

TeamCity build feature publishing a commit status to an external
system like GitHub, Gerrit Code Review tool, BitBucket Cloud or Atlassian Stash.

## Builds:

- [TeamCity 8.0.x or later](http://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamCityPluginsByJetBrains_CommitStatusPublisher_Master) ![](http://teamcity.jetbrains.com/app/rest/builds/buildType:TeamCityPluginsByJetBrains_Unsorted_CommitStatusPublisher/statusIcon)
- [TeamCity 7.1.x](http://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamCityPluginsByJetBrains_CommitStatusPublisher_71) ![](http://teamcity.jetbrains.com/app/rest/builds/buildType:TeamCityPluginsByJetBrains_Unsorted_CommitStatusPublisher71/statusIcon)

Found a bug? File [an issue](https://youtrack.jetbrains.com/newIssue?project=TW&clearDraft=true&c=Assignee+neverov&c=Subsystem+plugins%3A+other&c=tag+plugin_statusPublisher).

## How to Test

### Pre-requistes
1. Oracle Java installed and Java_Home environment variable set
2. TeamCity installed on your development machine
3. Apache Maven installed 

### Build
Go to the root directory of project and run
```
mvn package
```

The target directory of the project root will contain the commit-status-publisher.zip file, which is ready to be installed.

### Install the plugin to TeamCity

1. Copy the plugin zip to *&lt;TeamCity Data Directory&gt;/plugins* directory.
2. Restart the server and locate the *Commit Status Publisher* Plugin in the Administration|Plugins List to verify the plugin was installed correctly.