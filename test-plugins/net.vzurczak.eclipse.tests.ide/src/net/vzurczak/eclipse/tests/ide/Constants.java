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

/**
 * @author Vincent Zurczak - Linagora
 */
public interface Constants {

	// Plug-in ID
	String PLUGIN_ID = "net.vzurczak.eclipse.tests.ide";

    // SVN identifiers
    String SVN_USER = System.getenv( "org_svn_user" );
    String SVN_PWD = System.getenv( "org_svn_password" );

    // The SVN URL
	String SVN_URL = "something";
}
