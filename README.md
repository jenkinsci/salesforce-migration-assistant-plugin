# Salesforce Migration Assistant

This Jenkins plugin generates automatically deploys metadata changes to a Salesforce organization based on differences 
between two commits in Git. Instead of deploying a repository's contents every time a change is made, the plugin can
determine what metadata needs to be deployed and deleted and coordinate only those changes. This has the benefit of 
drastically reducing deployment times and uncoupling the reliance on the package manifest file (``package.xml``).

The plugin supports TestLevels in deployments. The options are: 
* None: No tests will be run during this deployment.
* Relevant: the RunSpecifiedTests level. Jenkins will use the information provided in the Test Regex field under the System Configuration section to determine which set of tests need to be run for this particular deployment. A warning will be generated in Jenkins log if no relevant test is found for a particular ApexClass.
* Local: All unit tests are run, excluding those found in managed packages.
* All: All unit tests are run, including those found in managed packages.

The plugin also has support for creating a "rollback" zip that contains all the necessary metadata required for
removing the changes made in the build from the Salesforce environment. This rollback package is a zip file located in
the job's workspace directory for that build, under ``/sma``.

The plugin supports use with [janinko's] (http://github.com/janinko/ghprb) popular [GitHub Pull Request Builder] (https://wiki.jenkins-ci.org/display/JENKINS/GitHub+pull+request+builder+plugin) plugin as well.


This plugin supports v34.0 of the Salesforce Metadata API. As such, there is not currently any support for Lightning 
Components deployment. Any aura definition bundles will be ignored by this plugin.

### Considerations

It should be noted that any problems that are traditionally encountered with the ANT migration tool and the migration of
certain metadata types (e.g. CustomObjects, Profiles, etc.) will not be solved with this tool. My suggestion would be to
plan the contents of your repository accordingly.

### Requirements
* Your SCM should be Git. This plugin will not work with any other SCMs.

### Required Jenkins Plugins
* [git plugin] (https://wiki.jenkins-ci.org/display/JENKINS/Git+Plugin)

### Pre-installation
* You should have some kind of Git repository that Jenkins has access to retrieve the supported Salesforce Metadata.

### Installation
* Install the plugin.
* There are some global configuration items that should be set.
    * Enter a Java regular expression to filter your repository for all your unmanaged package unit tests.
        * The default is ``.*[T|t]est.*``
    * Enter the Poll Wait value.
    * Enter the Max Poll value.
        * The default values for Poll Wait and Max Poll will allow a build to run for up to 100 minutes.
    * If you're behind a proxy, you can configure the settings for the proxy server, port, and authentication details
    under `Advanced...`

### Project Configuration
* Create a new job.
* Setup your Git SCM of choice.
* Add ``Salesforce Migration Assistant`` as a Build step.
    * Enter the username for the environment that you are deploying to.
    * Enter the password for the provided user.
    * Enter the security token for the user provided if the Salesforce org requires such.
    * Select the Salesforce instance type.
        * Support for Sandbox and Production orgs only.
    * Check whether you want SMA validate this build only.
    * Select the TestLevel that you would like the deployment to be set at.
    
### Parameterized Builds
If you would like to manually override some of the aspects of builds, particularly on manual builds, you can set them via
specific parameters.
* `SMA_DEPLOY_ALL_METADATA`: A boolean parameter that forces manually triggered builds to deploy the entire repository contents.
* `SMA_PREVIOUS_COMMIT_OVERRIDE`: A String parameter that allows manually triggered builds to be "diffed" against a particular commit
in your repository.

### Changelog

#### -> 2.1.2
* Prepare repository for jenkins-ci hosting.

#### -> 2.1.1
* Add support for proxy configurations.
* Require Jenkins v1.579.
* Bug fixes.

#### -> 2.1
* Add support for GitHub Pull Request Builder plugin.
* Add rollback zip generation support.
  * Was removed in previous release.
* Bug fixes.

#### -> 2.0
* Redesign of entire plugin.
* Plugin now uses the Salesforce Metadata API for deploying changes to an environment.
* Add support for TestLevels in deployments.
* Bug fixes.

#### -> 1.1.2
* Add support for overriding the previous commit sha and to force an initial build.
* Add support for parameter-izing said overrides.
    * `SMA_FORCE_INITIAL_BUILD` -> Boolean
    * `SMA_SHA_OVERRIDE` -> String
* Bug fixes.

#### -> 1.1.1
* Add global configuration for max poll, poll wait, and test regex.
* Add help text
* Add username, password, and server url to dynamically generated build.xml

#### -> 1.1
* Rename plugin
* Generate an Ant build.xml file
* Run unit tests for default namespace code i.e. no managed package tests

#### -> 1.0
* Initial Release
* Generate manifest package file based on git-diffs
* Generate a rollback package based on what was deployed/deleted/modified
* Create a deployment package in the job's workspace
* Create an updated version of package.xml if necessary
* Allow user to force initial build/commit behavior for a job at will
* Support for Metadata API v29.0

### Licensing

This software is licensed under the terms you may find in the file name "LICENSE.txt" in this directory.
