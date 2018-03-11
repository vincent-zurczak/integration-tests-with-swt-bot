/******************************************************************************
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * 		Vincent Zurczak - initial API and implementation
 *******************************************************************************/

package net.vzurczak.eclipse.tests.ide;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.eclipse.finder.waits.Conditions;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.exceptions.WidgetNotFoundException;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;

/**
 * @author Vincent Zurczak - Linagora
 */
public final class TestUtils {

    /**
     * Private empty constructor.
     */
    private TestUtils() {
        // nothing
    }


    /**
     * Finds a tree item from a list of partials labels.
     * <p>
     * Useful in resource trees that contain, as an example, revision numbers
     * in their labels. This method can be used to ignore this kind of suffix
     * by only considering the prefix (hence the partial label).
     * </p>
     *
     * @param tree a SWT tree
     * @param partialLabels a non-empty list of partial labels
     * @return a tree item
     * @throws WidgetNotFoundException if no item was found
     */
    public static SWTBotTreeItem fromPartialLabels( SWTBotTree tree, String... partialLabels ) {

        // Find the first item
        SWTBotTreeItem firstItem = null;
        for( final SWTBotTreeItem item : tree.getAllItems()) {
            final String itemText = fixNodeName( item.getText());
            if( itemText.equals(partialLabels[0])
                    || itemText.startsWith( partialLabels[ 0 ] + " " )) {
                firstItem = item;
                break;
            }
        }

        // If there are other labels, find in the children items
        SWTBotTreeItem current = firstItem;
        if( partialLabels.length > 1 ) {
            current = fromPartialLabels(
                firstItem,
                Arrays.copyOfRange(partialLabels, 1, partialLabels.length));
        }

        else if( current == null )
            throw new WidgetNotFoundException("No partial tree item was found with the path " + Arrays.toString(partialLabels));

        return current;
    }


    /**
     * Finds a tree item from a list of partials labels.
     * <p>
     * Useful in resource trees that contain, as an example, revision numbers
     * in their labels. This method can be used to ignore this kind of suffix
     * by only considering the prefix (hence the partial label).
     * </p>
     *
     * @param treeItem a SWT tree item
     * @param partialLabels a non-empty list of partial labels
     * @return a tree item
     * @throws WidgetNotFoundException if no item was found
     */
    public static SWTBotTreeItem fromPartialLabels( SWTBotTreeItem treeItem, String... partialLabels ) {

        SWTBotTreeItem current = treeItem;
        if( treeItem != null ) {
            for (final String partialLabel : partialLabels) {
                current.expand();

                final SWTBotTreeItem previous = current;
                for( final String nodeName : current.getNodes()) {
                    final String fixedNodeName = fixNodeName(nodeName);
                    if( fixedNodeName.equals(partialLabel)
                            || fixedNodeName.startsWith( partialLabel + " " )) {
                        current = current.getNode(nodeName);
                        break;
                    }
                }

                if( previous.equals(current))
                    throw new WidgetNotFoundException("Node " + partialLabel + " was not found.");
            }
        }

        if( current == null )
            throw new WidgetNotFoundException("No partial tree item was found with the path " + Arrays.toString(partialLabels));

        return current;
    }


    /**
     * Finds a tree item from a list of partials labels.
     * <p>
     * Useful in resource trees that contain, as an example, revision numbers
     * in their labels. This method can be used to ignore this kind of suffix
     * by only considering the prefix (hence the partial label).
     * </p>
     * <p>
     * See {@link #fromPartialLabels(SWTBotTree, String...)}
     * and {@link #fromPartialLabels(SWTBotTreeItem, String...)}.
     * </p>
     *
     * @param parent a SWT tree or a tree item
     * @param partialLabels a non-empty list of partial labels
     * @return a tree item
     * @throws WidgetNotFoundException if no item was found
     */
    public static SWTBotTreeItem fromPartialLabels( Object parent, String... partialLabels ) {

    	SWTBotTreeItem result = null;
    	if( parent instanceof SWTBotTree )
    		result = fromPartialLabels((SWTBotTree) parent, partialLabels );
    	else if( parent instanceof SWTBotTreeItem )
    		result = fromPartialLabels((SWTBotTreeItem) parent, partialLabels );

    	return result;
    }


    /**
     * Remove potential SVN annotations from labels.
     * @param nodeName the node name
     * @return the clean name
     */
    private static String fixNodeName(String nodeName) {
        return nodeName.replace(">", "").trim();
    }


    /**
     * Selects an item in the package explorer.
     * <p>
     * This method works, no matter the perspective.
     * </p>
     *
     * @param bot the SWT bot
     * @param path a non-empty list of partial labels to navigate in the explorer
     * @return a tree item
     * @throws WidgetNotFoundException if an item was not found
     */
    public static SWTBotTreeItem selectElementInPackageExplorer( SWTWorkbenchBot bot, String... path ) {

        // Open the Java perspective
        openJavaPerpsective(bot);
        bot.viewByPartName("Package Explorer").show();
        bot.viewByPartName("Package Explorer").setFocus();

        // Find the element
        final SWTBotTreeItem javaItem = fromPartialLabels( bot.tree(), path).expand();
        return javaItem;
    }


    /**
     * Selects an item in the SVN history.
     * <p>
     * This method handles network latency and the reactivity of the
     * SVN repository.
     * </p>
     *
     * @param bot the SWT bot
     * @param svnView the SVN history view
     * @param path a non-empty list of partial labels to navigate in the view
     * @return a tree item
     * @throws WidgetNotFoundException if an item was not found
     */
    public static SWTBotTreeItem selectElementInSvnHistory( SWTWorkbenchBot bot, SWTBotView svnView, String... path ) {

    	svnView.setFocus();
    	SWTBotTreeItem currentItem = null;
    	for( final String s : path ) {

    		// Wait for the label to be visible in the tree
	    	final Object parent = currentItem != null ? currentItem : svnView.bot().tree();
	        bot.waitUntil(new DefaultCondition()
	        {
	            @Override
	            public boolean test() throws Exception
	            {
	            	boolean found = false;
	            	try {
						fromPartialLabels( parent, s );
						found = true;

					} catch( Exception e ) {
						// nothing
					}

	            	return found;
	            }

	            @Override
	            public String getFailureMessage()
	            {
	                return "Item '" + s + "' could not be found in the SVN history.";
	            }
	        }, 10000, 500 );

	        // Once it is visible, expand it and prepare the next iteration
	    	currentItem = fromPartialLabels( parent, s );
	    	currentItem.expand();
    	}

    	return currentItem;
    }


    /**
     * Opens the SVN exploring perspective.
     * @param bot the SWT bot
     */
    public static void openSvnExplorePerpsective(SWTWorkbenchBot bot) {
        openPerspective(bot, "SVN Repository Exploring");
    }


    /**
     * Opens the Java perspective.
     * @param bot the SWT bot
     */
    public static void openJavaPerpsective(SWTWorkbenchBot bot) {
        openPerspective(bot, "Java");
    }


    /**
     * Opens a perspective.
     * @param bot the SWT bot
     * @param perspectiveName the perspective name
     */
    public static void openPerspective(SWTWorkbenchBot bot, String perspectiveName) {

        bot.menu("Window").menu("Perspective").menu("Open Perspective").menu("Other...").click();
        bot.shell("Open Perspective").activate();
        bot.table().select(perspectiveName);

        final SWTBotShell shell = bot.activeShell();
        bot.button("Open").click();
        bot.waitUntil(Conditions.shellCloses(shell), 5000);
    }


    /**
     * Waits for the initial setup for the workspace has completed.
     * @param bot the SWT bot
     */
    public static void waitForToolsToBeReady(SWTWorkbenchBot bot) {

    	bot.waitUntil( new DefaultCondition() {

			@Override
			public boolean test() throws Exception {

				// Tools are located in the parent's directory of the workspace
				IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
				File workspace = workspaceRoot.getLocation().toFile();
				boolean result = true;

				result = result && new File( workspace, "../servers/tomcat/apache-tomcat-6.0.18" ).isDirectory();
				result = result && new File( workspace, "../servers/tomcat/apache-tomcat-7" ).isDirectory();
				result = result && new File( workspace, "../jdk/jdk7/bin" ).isDirectory();
				result = result && new File( workspace, "../jdk/jdk8/bin" ).isDirectory();
				result = result && new File( workspace, "../tools/maven/apache-maven-3.3.9" ).isDirectory();
				result = result && new File( workspace, "../tools/maven/conf/settings.xml" ).isFile();

				result = result && new File( workspace, "Servers" ).isDirectory();
				result = result && new File( workspace, "Database" ).isDirectory();

				result = result && workspaceRoot.getProject( "Database" ).exists();
				result = result && workspaceRoot.getProject( "Servers" ).exists();

				return result;
			}

			@Override
			public String getFailureMessage() {
				return "Tools were not installed by OOMPH.";
			}

		}, 60 * 1000, 2000 );
    }


    /**
     * Executes a set of common actions and checks for all the IDE tests.
     * @param bot the SWT bot
     */
    public static void beforeTest(SWTWorkbenchBot bot) {

    	// Reset the workbench
    	bot.resetWorkbench();

    	// Close the welcome page
		try {
			bot.viewByTitle("Welcome").close();

		} catch( Exception e ) {
			// ignore
		}

		// Wait for the workspace initialization
		waitForToolsToBeReady(bot);
    }


    /**
     * Waits for the compilation to complete.
     * @param bot the SWT bot
     * @param minutesToWait the number of minutes to wait
     */
    public static void waitForBuildToComplete( SWTWorkbenchBot bot, long minutesToWait ) {

    	bot.waitUntil( new DefaultCondition() {

            @Override
            public boolean test() throws Exception {

            	IJobManager jobManager = Job.getJobManager();
                Job[] autoBuilJobs = jobManager.find(ResourcesPlugin.FAMILY_AUTO_BUILD);
                Job[] manualBuilJobs = jobManager.find(ResourcesPlugin.FAMILY_MANUAL_BUILD);

                return manualBuilJobs.length == 0 && autoBuilJobs.length == 0;
            }

            @Override
            public String getFailureMessage() {
                return "The project could not be built.";
            }
        }, minutesToWait * 60  * 1000, 3000 );
    }


    /**
     * Waits for Maven projects have been imported.
     * @param bot the SWT bot
     * @param minutesToWait the number of minutes to wait
     */
    public static void waitForMavenImportToComplete( SWTWorkbenchBot bot, long minutesToWait ) {

    	bot.waitUntil( new DefaultCondition() {

            @Override
            public boolean test() throws Exception {

            	IJobManager jobManager = Job.getJobManager();
                List<String> mavenJobNames = Arrays.asList(
                		"updating maven project",
                		"importing maven projects",
                		"updating maven profiles",
                		"checking out maven" );

            	for( Job job : jobManager.find(null)) {
                	for( String mavenJobName : mavenJobNames ) {
                		if( job.getName().toLowerCase().startsWith( mavenJobName )) {
                			if( job.getState() != Job.NONE )
                				return false;
                		}
                	}
                }

                return true;
            }

            @Override
            public String getFailureMessage() {
                return "The project could not be built.";
            }
        }, minutesToWait * 60  * 1000, 3000 );
    }


    /**
     * Deploys a project on Tomcat 7.
     * @param bot the SWT bot
     * @param item the tree item that corresponds to the project to deploy
     * @param run true for run mode, false for debug mode
     */
    public static void deployOnTomcat7( SWTWorkbenchBot bot, SWTBotTreeItem item, boolean run ) {

    	// Menu
    	if(run)
    		item.contextMenu("Run As").menu("1 Run on Server").click();
    	else
    		item.contextMenu("Debug As").menu("1 Debug on Server").click();

		bot.tree().getTreeItem("localhost").getNode("apache-tomcat-7").select();
        bot.button("Finish").click();

        // Sometimes, we are asked whether Tomcat should be restarted
        try {
			bot.button("OK").click();

		} catch( Exception e ) {
			// ignore
		}
    }


    /**
     * Is our web application online?
     * @param url an URL
     * @return true if the HTTP code is OK, false otherwise
     * @throws Exception
     */
    public static boolean isWebAppliationonline( String url ) throws Exception {

		HttpURLConnection conn = (HttpURLConnection) new URL( url ).openConnection();
    	conn.setRequestMethod("HEAD");
    	return conn.getResponseCode() == HttpURLConnection.HTTP_OK;
	}


    // Classes copied from Roboconf's source code.
    // See https://github.com/roboconf/roboconf-platform/blob/master/core/roboconf-core/src/main/java/net/roboconf/core/utils/Utils.java


    /**
     * Closes a stream quietly.
     * @param in an input stream (can be null)
     */
    public static void closeQuietly(InputStream in) {

    	if (in != null) {
            try {
                in.close();

            } catch (final IOException e) {
                // nothing
            }
        }
    }

    /**
     * Closes a stream quietly.
     * @param out an output stream (can be null)
     */
    public static void closeQuietly(OutputStream out) {

    	if (out != null) {
            try {
                out.close();

            } catch (final IOException e) {
                // nothing
            }
        }
    }

    /**
     * Closes a reader quietly.
     * @param reader a reader (can be null)
     */
    public static void closeQuietly(Reader reader) {

        if (reader != null) {
            try {
                reader.close();

            } catch (final IOException e) {
                // nothing
            }
        }
    }

    /**
     * Closes a writer quietly.
     * @param writer a writer (can be null)
     */
    public static void closeQuietly(Writer writer) {

    	if (writer != null) {
            try {
                writer.close();

            } catch (final IOException e) {
                // nothing
            }
        }
    }

    /**
     * Copies the content from in into os.
     * <p>
     * Neither <i>in</i> nor <i>os</i> are closed by this method.<br>
     * They must be explicitly closed after this method is called.
     * </p>
     * <p>
     * Be careful, this method should be avoided when possible. It was responsible for memory leaks. See #489.
     * </p>
     *
     * @param in an input stream (not null)
     * @param os an output stream (not null)
     * @throws IOException if an error occurred
     */
    public static void copyStreamUnsafelyUseWithCaution(InputStream in, OutputStream os) throws IOException {

        final byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            os.write(buf, 0, len);
        }
    }

    /**
     * Copies the content from in into os.
     * <p>
     * This method closes the input stream. <i>os</i> does not need to be closed.
     * </p>
     *
     * @param in an input stream (not null)
     * @param os an output stream (not null)
     * @throws IOException if an error occurred
     */
    public static void copyStreamSafely(InputStream in, ByteArrayOutputStream os) throws IOException {
        try {
            copyStreamUnsafelyUseWithCaution(in, os);

        } finally {
            in.close();
        }
    }

    /**
     * Copies the content from in into outputFile.
     * <p>
     * <i>in</i> is not closed by this method.<br>
     * It must be explicitly closed after this method is called.
     * </p>
     *
     * @param in an input stream (not null)
     * @param outputFile will be created if it does not exist
     * @throws IOException if the file could not be created
     */
    public static void copyStream(InputStream in, File outputFile) throws IOException {

    	final OutputStream os = new FileOutputStream(outputFile);
        try {
            copyStreamUnsafelyUseWithCaution(in, os);

        } finally {
            os.close();
        }
    }

    /**
     * Copies the content from inputFile into outputFile.
     *
     * @param inputFile an input file (must be a file and exist)
     * @param outputFile will be created if it does not exist
     * @throws IOException if something went wrong
     */
    public static void copyStream(File inputFile, File outputFile) throws IOException {

    	final InputStream is = new FileInputStream(inputFile);
        try {
            copyStream(is, outputFile);

        } finally {
            is.close();
        }
    }

    /**
     * Copies the content from inputFile into an output stream.
     *
     * @param inputFile an input file (must be a file and exist)
     * @param os the output stream
     * @throws IOException if something went wrong
     */
    public static void copyStream(File inputFile, OutputStream os) throws IOException {

    	final InputStream is = new FileInputStream(inputFile);
        try {
            copyStreamUnsafelyUseWithCaution(is, os);

        } finally {
            is.close();
        }
    }

    /**
     * Writes a string into a file.
     *
     * @param s the string to write (not null)
     * @param outputFile the file to write into
     * @throws IOException if something went wrong
     */
    public static void writeStringInto(String s, File outputFile) throws IOException {
        final InputStream in = new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
        copyStream(in, outputFile);
    }

    /**
     * Appends a string into a file.
     *
     * @param s the string to write (not null)
     * @param outputFile the file to write into
     * @throws IOException if something went wrong
     */
    public static void appendStringInto(String s, File outputFile) throws IOException {

        OutputStreamWriter fw = null;
        try {
            fw = new OutputStreamWriter(new FileOutputStream(outputFile, true), StandardCharsets.UTF_8);
            fw.append(s);

        } finally {
            closeQuietly(fw);
        }
    }

    /**
     * Reads a text file content and returns it as a string.
     * <p>
     * The file is tried to be read with UTF-8 encoding. If it fails, the default system encoding is used.
     * </p>
     *
     * @param file the file whose content must be loaded
     * @return the file content
     * @throws IOException if the file content could not be read
     */
    public static String readFileContent(File file) throws IOException {

        String result = null;
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        copyStream(file, os);
        result = os.toString("UTF-8");

        return result;
    }
}
