# Integration Tests with SWT Bot

> This project is part of resources for [this blog article](https://vzurczak.wordpress.com/2018/03/13/testing-eclipses-user-workflows-from-oomph-to-subversive-m2e-and-wtp/).

A multi-module project that demonstrates the use Maven, ANT and
SWT Bot for integration tests about Eclipse tools. They verify the Eclipse
installer (OOMPH) and various tools, including Subversive, m2e, WTP, through
various scenarios, all of them in interaction with a private environment (SVN server,
Nexus repository, internal proxy, etc).

These tests can be used to verify a new catalog, a new set of setups tasks or
new user scenarios.

This repository contains:

* **test-plugins**: SWT Bot tests. One project to test the Eclipse installer (OOMPH) and one to test the installed Eclipse.
* **test-features**: the matching features, one for the installer, one for the IDE.
* **test-update-site**: the update site for these plug-ins and features.
* **tests-execution**: the modules that prepare the tests and run them.  
There is one module to prepare and execute the tests for the instaler.  
There is another one to prepare the tests for the IDE. Eventually, there is a last one to
run the tests for the IDE. These two last ones were separated because IDE tests are complex
and must be resumable (whereas the preparation is done only once).


## Deal with the Installer and Prepare the IDE

This sequence compiles tests, gets the installer to run, configures it for tests
(install SWT Bot and the test feature), execute the tests (that install a brand
new Eclipse IDE) and eventually prepares the IDE for the next steps.

```
mvn clean verify \
	-Dorg.setups.location="file:/home/vzurczak/workspaces/git.setups/target/catalog/" \
	-Dorg.installer.location="file:/home/vzurczak/workspaces/git.installer/target/eclipse-inst-linux64.tar.gz" \
	-P setup-tests \
	-P <env>
```

The properties are...

* **org.setups.location**: the location of the OOMPH catalog.
* **org.installer.location**: the location of the installer (tar.gz).


## Execute IDE Tests

This sequence can only be run after the IDE was installed and configured.  
Since user scenarios can be quite long and complex, each one can be run from a specific
Maven profile. It makes it easy to relaunch a failed test. Every relaunch resets the workspace.

In case of failure, the workspace is saved.  
Therefore, it is easy to introspect it. The IDE can even be started by hand for debug purpose.
It is saved under **tests-execution/tests-execution-installer/target/eclipse**. The workspace is
located under **tests-execution/tests-execution-installer/target/eclipse/workspace**

Example to launch tests for a given scenario.

```
mvn clean verify -P tests-scenario1
```


## Iterative Development of the Tests

SWT Bot tests can generally be run from Eclipse.  
It is not true for these tests. Since they test a specific IDE, with
specific preferences (set by OOMPH), you cannot run them from any Eclipse. 

To make their development as much easy as possible, all the user scenarios have their own
Maven profile. And another profile was defined to recompile tests and upgrade the tests plug-ins
in the IDE (which avoids going through the installer once again). It must be used along with
the profile of a user scenario.

```
mvn clean verify \
	-P recompile-ide-tests
	-P tests-for-scenario1 \
	-P <env>
```


## Warning

When OOMPH is launched from these tests, the installer does not save the IDE
where we want. We configured the tests so that everything is coherent, but for some
reason, OOMPH does not behave exactly the same way that when it is run by hand (only
for the install location). Maybe it comes from our custom catalog.
