/******************************************************************************
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * 		Vincent Zurczak - initial API and implementation
 *******************************************************************************/

package net.vzurczak.eclipse.tests.installer.tests;

import java.io.File;
import java.net.URI;

import org.eclipse.core.runtime.Platform;
import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.utils.SWTBotPreferences;
import org.eclipse.swtbot.swt.finder.waits.Conditions;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * A unit test that installs a brand new Eclipse from the installer.
 * @author Vincent Zurczak - Linagora
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class EclipseInstallTest {

	private static SWTBot bot;

	@BeforeClass
	public static void beforeClass() throws Exception {
		bot = new SWTBot();
	}


	@Test
	public void test() throws Exception {

		// Configure the bot's speed
		// (see https://wiki.eclipse.org/SWTBot/FAQ#Can_I_slow_down_the_execution_speed_of_SWTBot_tests.3F)
		SWTBotPreferences.PLAYBACK_DELAY = 30;

		// Select the product
		bot.waitUntil( Conditions.shellIsActive( "Eclipse Installer" ));
		bot.tree( 0 ).setFocus();
		bot.tree( 0 ).expandNode( "My IDE", "My IDE's name" ).select();
		bot.comboBox( 0 ).setSelection( "Eclipse Oxygen" );
		bot.button( "Next >" ).click();

		// Select the last two JDKs, DBeaver, the last version of Maven, Tomcat 6 and 7.
		bot.tree( 0 ).setFocus();
		bot.tree( 0 ).expandNode( "Tools for my IDE", "JDKs", "JDK 7" ).check();
		bot.tree( 0 ).expandNode( "Tools for my IDE", "JDKs", "JDK 8" ).check();
		bot.tree( 0 ).expandNode( "Tools for my IDE", "Application Servers", "Tomcat 6" ).check();
		bot.tree( 0 ).expandNode( "Tools for my IDE", "Application Servers", "Tomcat 7" ).check();
		bot.tree( 0 ).expandNode( "Tools for my IDE", "Eclipse Plug-ins", "DBeaver").check();
		bot.tree( 0 ).expandNode( "Tools for my IDE", "Maven", "Maven 3.3.9").check();
		bot.button( "Next >" ).click();

		// Fill-in the install properties
		// (OOMPH sometimes hides fields, so show all the variables).
		bot.checkBox( "Show all variables" ).select();

		// Install under the "target" directory.
		URI ideDirectoryUri = Platform.getInstallLocation().getURL().toURI().resolve( "../eclipse" );
		File ideDirectory = new File( ideDirectoryUri );
		//Assert.assertTrue( ideDirectory.getAbsolutePath() + " does not exist.", ideDirectory.isDirectory());

		bot.textWithLabel( "Installation Directory:" ).setText( ideDirectory.getAbsolutePath());
		bot.textWithLabel( "Identifier:" ).setText( "bot@eclipse.org" );
		bot.button( "Next >" ).click();

		// Do not generate any master password.
		// The question might be skipped because OOMPH keeps
		// a cache under ~/.eclipse. It is generally better to clean
		// this directory before running these tests. It is what
		// the ANT script does by default, it deletes this cache.

		// Since the master password is stored in ~/.eclipse/org.eclipse.equinox.security
		// and that it might be used by other Eclipse installations, we do not delete
		// it in the ANT script. So, if the question is asked, SWT Bot will answer "no".
		// And if the question is never raised, we will directly complete the wizard.
		try {
			bot.button( "No" ).click();

		} catch( Throwable t ) {
			// ignore
		}

		// Complete the installation
		bot.button( "Finish" ).click();

		// We are asked about the Eclipse license
		bot.button( "Accept Now" ).click();

		// Do not launch Eclipse at the end
		bot.checkBox( "Launch automatically" ).deselect();

		// Accept all the certificates (wait up to 60 seconds for the dialog to show up)
		bot.waitUntil( Conditions.shellIsActive( "Do you trust these certificates?" ), 60000 );
		bot.button( "Select All" ).click();
		bot.button( "Accept selected" ).click();

		// Wait for it to complete - max timeout = 50s
		// (if everything went well, the "finish" button should be enabled)
		bot.waitUntil( new DefaultCondition() {

			@Override
			public boolean test() throws Exception {
				return this.bot.button( "Finish" ).isEnabled();
			}

			@Override
			public String getFailureMessage() {
				return "The finish button should have been enabled at the end.";
			}
		}, 50000 );

		bot.button( "Finish" ).click();
	}


	@AfterClass
	public static void sleep() {
		bot.sleep(2000);
	}
}
