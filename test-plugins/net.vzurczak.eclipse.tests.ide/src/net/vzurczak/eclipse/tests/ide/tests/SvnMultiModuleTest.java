/******************************************************************************
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * 		Vincent Zurczak - initial API and implementation
 *******************************************************************************/

package net.vzurczak.eclipse.tests.ide.tests;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.waits.Conditions;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTableItem;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import net.vzurczak.eclipse.tests.ide.Constants;
import net.vzurczak.eclipse.tests.ide.TestUtils;

/**
 * Just a sample test that retrieves a multi-module project and verifies SVN tooling features.
 * <p>
 * The scenario is made up of several complex steps.
 * Each step is described in its own method.
 * </p>
 *
 * @author Vincent Zurczak - Linagora
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class SvnMultiModuleTest {

    private static final int PROJECTS_TO_WAIT = 5;
	private static SWTWorkbenchBot	bot;

	private static final String COMM = "// This is a comment\n";
	private static final String PARAMETRAGE_PACKAGE_IN_SVN = "my-project/src/main/java/net/vzurczak/my/package";
	private static final String[] PARAMETRAGE_PACKAGE_PATH_IN_JAVA =
	{ "my-project", "src/main/java", "net.vzurczak.my.package" };

	private String revision;


	@BeforeClass
	public static void beforeClass() throws Exception {
		bot = new SWTWorkbenchBot();
		TestUtils.beforeTest( bot );
	}


	@AfterClass
    public static void sleep() {
        bot.sleep(2000);
    }


	@Test
	public void test() throws Exception {

	    // Bot speed
        // (see https://wiki.eclipse.org/SWTBot/FAQ#Can_I_slow_down_the_execution_speed_of_SWTBot_tests.3F)
        SWTBotPreferences.PLAYBACK_DELAY = 30;

	    // One method = one step in the scenario
	    step_01_importProject();
	    step_02_ignorePatterns();
	    step_03_dataSynchronization();
	    step_04_compareDifferences();
	    step_05_commitFile();
	    step_06_showHistory();
	    step_07_updateProject();
	}


	/**
	 * Gets the project from SVN and imports it in the work space.
	 */
	public void step_01_importProject() throws Exception {

	    final IWorkspaceRoot workspace = ResourcesPlugin.getWorkspace().getRoot();
        final int countBefore = workspace.getProjects().length;

	    // Add the SVN repository
        TestUtils.openSvnExplorePerpsective(bot);
	    final SWTBotView svnView = bot.viewByTitle( "SVN Repositories" );
	    svnView.show();

	    bot.toolbarButtonWithTooltip("New Repository Location").click();
	    bot.comboBox(0).setText(Constants.SVN_URL);
	    bot.comboBox(1).setText(Constants.SVN_USER);
        bot.textWithLabel("&Password:").setText(Constants.SVN_PWD);
        bot.checkBox("Save authentication (could trigger secure storage login)").select();
	    bot.button("Finish").click();

	    // Get the project
	    svnView.bot().tree( 0 ).setFocus();
	    svnView.bot().tree( 0 ).expandNode(Constants.SVN_URL).contextMenu("Check out as Maven Project...").click();
	    bot.button("Finish").click();

	    // Wait for the Maven modules to be imported
        bot.waitUntil( new DefaultCondition() {

            @Override
            public boolean test() throws Exception {
                return workspace.getProjects().length >= countBefore + PROJECTS_TO_WAIT;
            }

            @Override
            public String getFailureMessage() {
                return "Projects were not checked out at all or not fast enough.";
            }
        }, 50000, 1000 );

        TestUtils.waitForMavenImportToComplete( bot, 10 );
        TestUtils.waitForBuildToComplete( bot, 5 );

        // Verify the projects creation
	    Assert.assertTrue(workspace.getProject("my-project").exists());
	}


	/**
	 * Configures team preferences.
	 */
    public void step_02_ignorePatterns() throws Exception {

        // Open the preferences dialog
        bot.menu("Window").menu("Preferences").click();
        bot.tree().getTreeItem("Team").expand();
        bot.tree().getTreeItem("Team").getNode("Ignored Resources").select();

        // Preferences may have been initialized during a previous run.
        // so, make their definition robust enough.
        final String[] toIgnore = { "**/target", "*.class" };
        for( final String itemToIgnore : toIgnore ) {

            // Create the preference only if it does not exist
            SWTBotTableItem item;
            try {
                item = bot.table().getTableItem(itemToIgnore);
            }
            catch (final Exception e) {
                bot.button("Add Pattern...").click();
                bot.textWithLabel("Enter a name or path pattern (* = any string, ? = any character):").setText(itemToIgnore);
                bot.button("OK").click();
                item = bot.table().getTableItem(itemToIgnore);
            }

            if( ! item.isChecked()) {
                item.select();
            }

            // It must be enabled
            Assert.assertTrue( itemToIgnore, bot.table().getTableItem(itemToIgnore).isChecked());
        }

        // Save
        bot.button("Apply and Close").click();

        // Do not share preferences with other workspaces
        try {
			bot.button("Ask Me Later").click();

		} catch( Exception e ) {
			// Ignore
		}

        // Same thing about the preferences recorder
        while(true) {
        	try {
				bot.button( "Cancel" ).click();

			} catch( Exception e ) {
				break;
			}
        }
    }


    /**
     * Tests synchronization with the SVN repository.
     */
    public void step_03_dataSynchronization() throws Exception {

        // Find the file in the workbench
        final SWTBotTreeItem javaItem = TestUtils.selectElementInPackageExplorer(bot, PARAMETRAGE_PACKAGE_PATH_IN_JAVA);

        // Open the files programmatically and update them.
        final String[] classes = {"ParametrageFactory.java", "ParametresExternes.java"};
        for( final String clazz : classes ) {
            TestUtils.fromPartialLabels(javaItem, clazz).contextMenu("Open").click();
            final IFile ifile = (IFile) ResourcesPlugin.getWorkspace().getRoot().findMember(new Path(PARAMETRAGE_PACKAGE_IN_SVN).append(clazz));
            Assert.assertNotNull( ifile );

            String text = TestUtils.readFileContent(ifile.getLocation().toFile());
            if( text.startsWith(COMM))
                text = text.replaceFirst(COMM, "");
            else
                text = COMM + text;
            ifile.setContents(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), true, true, null);
            ifile.getParent().refreshLocal(IResource.DEPTH_INFINITE, null);
            bot.sleep(1000);
        }

        // Close all the editors
        bot.closeAllEditors();

        // Synchronize
        SWTBotView view = bot.viewByPartName("Package Explorer");
        view.setFocus();

        bot.tree().getAllItems()[ 0 ].select().contextMenu("Team").contextMenu("Update").click();
        bot.tree().getAllItems()[ 0 ].contextMenu("Team").contextMenu("Synchronize with Repository").click();
        bot.button("Yes").click();

        view = bot.viewByPartName("Synchronize");
        view.show();
        view.setFocus();

        // Verify only the right items are found as not synchronized
        final SWTBotTreeItem svnItem = TestUtils.fromPartialLabels( view.bot().tree(), "my-project", PARAMETRAGE_PACKAGE_IN_SVN ).expand();
        Assert.assertEquals(2, svnItem.getItems().length);
        Assert.assertEquals("ParametrageFactory.java", svnItem.getItems()[0].getText());
        Assert.assertEquals("ParametresExternes.java", svnItem.getItems()[1].getText());

        Assert.assertEquals(1, bot.tree().getTreeItem("my-project").getItems().length);
        Assert.assertEquals(
            PARAMETRAGE_PACKAGE_IN_SVN,
            bot.tree().getTreeItem("my-project").getItems()[0].getText());

        Assert.assertEquals(1, bot.tree().getAllItems().length);
    }


    /**
     * Compares resources in editors.
     */
    public void step_04_compareDifferences() throws Exception {

        // Open the file in the compare editor
        bot.viewByPartName("Synchronize").setFocus();
        final SWTBotTreeItem svnItem = TestUtils.fromPartialLabels( bot.tree(), "my-project", PARAMETRAGE_PACKAGE_IN_SVN ).expand();
        TestUtils.fromPartialLabels(svnItem, "ParametresExternes.java").contextMenu("Open In Compare Editor").click();

        // Use editor actions
        bot.editorByTitle("ParametresExternes.java").show();
        bot.toolbarButtonWithTooltip("Copy Current Change from Right to Left").click();
        bot.toolbarButtonWithTooltip("Save (Ctrl+S)").click();

        // Synchronize
        bot.viewByPartName("Synchronize").setFocus();
        final SWTBotTreeItem myProjectItem = TestUtils.fromPartialLabels( bot.tree(), "my-project" );
        myProjectItem.contextMenu("Update").click();
        bot.button("OK").click();
        myProjectItem.contextMenu("Synchronize").click();

        // Verify the list
        Assert.assertEquals( 1, svnItem.getItems().length );
        Assert.assertEquals("ParametrageFactory.java", svnItem.getItems()[0].getText());

        Assert.assertEquals(1, myProjectItem.getItems().length);
        Assert.assertEquals(PARAMETRAGE_PACKAGE_IN_SVN, myProjectItem.getItems()[0].getText());

        Assert.assertEquals(1, bot.tree().getAllItems().length);
    }


    /**
     * Commits modifications.
     */
    public void step_05_commitFile() throws Exception {

        // Commit
        bot.viewByPartName("Synchronize").setFocus();
        final SWTBotTreeItem svnItem = TestUtils.fromPartialLabels( bot.tree(), "my-project", PARAMETRAGE_PACKAGE_IN_SVN ).expand();
        TestUtils.fromPartialLabels(svnItem, "ParametrageFactory.java").contextMenu("Commit...").click();
        bot.styledText().setText("Test commit");

        // Only one file in the box
        Assert.assertEquals( 1, bot.table().rowCount());
        Assert.assertEquals( "my-project/" + PARAMETRAGE_PACKAGE_IN_SVN + "/ParametrageFactory.java", bot.table().cell(0, 1));

        // We use a test repository, we can commit for real
        final SWTBotShell shell = bot.activeShell();
        bot.button("OK").click();
        bot.waitUntil(Conditions.shellCloses(shell), 5000);

        // Update and synchronize
        Thread.sleep(5000);
        TestUtils.selectElementInPackageExplorer(bot, "my-project");
        bot.tree().getAllItems()[ 0 ].select().contextMenu("Team").menu("Update").click();
        bot.tree().getAllItems()[ 0 ].contextMenu("Team").menu("Synchronize with Repository").click();
        bot.button("Yes").click();
        bot.viewByPartName("Synchronize").setFocus();

        // Wait for synchronization to complete
        bot.waitUntil(new DefaultCondition()
        {

            @Override
            public boolean test() throws Exception
            {
                return "No changes in 'SVN (my-project)'.".equals(this.bot.label(2).getText());
            }

            @Override
            public String getFailureMessage()
            {
                return "A message was expected to indicate that no changes were found.";
            }
        }, 5000);
    }


    /**
     * Verifies the last commit appears in the history.
     */
    public void step_06_showHistory() throws Exception {

        // Find the file in the workbench
        final SWTBotTreeItem javaItem = TestUtils.selectElementInPackageExplorer(bot, PARAMETRAGE_PACKAGE_PATH_IN_JAVA);
        TestUtils.fromPartialLabels(javaItem, "ParametrageFactory.java").select().contextMenu("Show History").click();

        // Check the recent history
        final SWTBotView view = bot.viewByPartName("History");
        view.show();
        view.setFocus();

        // Wait for the history to be displayed
        bot.waitUntil(new DefaultCondition()
        {

            @Override
            public boolean test() throws Exception
            {
                return view.bot().tree().getAllItems().length > 5;
            }

            @Override
            public String getFailureMessage()
            {
                return "Waiting for history to show up timed out.";
            }
        }, 10000);

        final SWTBotTreeItem revisionItem = view.bot().tree().getAllItems()[2];
        revisionItem.select();
        revisionItem.contextMenu("Open").click();
        bot.editorByTitle("ParametrageFactory.java [Rev:" + revisionItem.getText() + "]").show();

        // Save the revision for later
        this.revision = revisionItem.getText();
    }


    /**
     * Deletes and reimport the project.
     */
    public void step_07_updateProject() throws Exception {

        // Delete everything
    	bot.closeAllEditors();
        for( IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
        	project.delete( true, null );
        }

        bot.waitUntil( new DefaultCondition() {
			@Override
			public boolean test() throws Exception {
				return ResourcesPlugin.getWorkspace().getRoot().getProjects().length == 0;
			}

			@Override
			public String getFailureMessage() {
				return "Projects failed to be deleted.";
			}
		}, 30000, 1000 );

        // Reimport the project, but at a previous revision
        TestUtils.openSvnExplorePerpsective(bot);
        bot.viewByPartName("SVN Repositories").show();
        bot.tree().getTreeItem(Constants.SVN_URL).expand().contextMenu("Check out as Maven Project...").click();
        bot.checkBox("Check out Head Revision").deselect();
        bot.textWithLabel("&Revision:").setText(this.revision);
        bot.button("Finish").click();

        // Wait for the project to be imported
        final IWorkspaceRoot workspace = ResourcesPlugin.getWorkspace().getRoot();
        bot.waitUntil( new DefaultCondition() {

            @Override
            public boolean test() throws Exception {
                return workspace.getProjects().length >= PROJECTS_TO_WAIT;
            }

            @Override
            public String getFailureMessage() {
                return "Projects were not checked out at all or not fast enough.";
            }
        }, 50000, 1000 );

        TestUtils.waitForBuildToComplete( bot, 5 );

        // Workbench test
        final SWTBotTreeItem javaItem = TestUtils.selectElementInPackageExplorer(bot, PARAMETRAGE_PACKAGE_PATH_IN_JAVA);
        final SWTBotTreeItem classItem = TestUtils.fromPartialLabels(javaItem, "ParametrageFactory.java").expand();

        // Wait for the revision to appear in the package explorer
        bot.waitUntil(new DefaultCondition()
        {
            @Override
            public boolean test() throws Exception
            {
                return ! classItem.getText().endsWith(".java");
            }

            @Override
            public String getFailureMessage()
            {
                return "Revision check could not be performed.";
            }
        }, 10000);

        Assert.assertTrue(
        		classItem.getText() + "(expected revision: " + this.revision + ")",
        		classItem.getText().startsWith("ParametrageFactory.java " + this.revision));

        // Update the project to the latest revision
        TestUtils.fromPartialLabels(bot.tree(), "my-project").select().contextMenu("Team").contextMenu("Update").click();
        bot.waitUntil(new DefaultCondition()
        {
            @Override
            public boolean test() throws Exception
            {
                return ! classItem.getText().endsWith(".java")
                    && ! classItem.getText().startsWith("ParametrageFactory.java " + SvnMultiModuleTest.this.revision);
            }

            @Override
            public String getFailureMessage()
            {
                return "Revision number was supposed to change.";
            }
        }, 20000, 1000);

        Assert.assertFalse(
        		classItem.getText() + "(stored revision: " + this.revision + ")",
        		classItem.getText().startsWith("ParametrageFactory.java " + this.revision));

        // The new revision should be "revision + 2"
        int expectedRevision = Integer.parseInt(this.revision.trim()) + 2;
        Assert.assertTrue(classItem.getText(), classItem.getText().startsWith("ParametrageFactory.java " + expectedRevision));
    }
}
