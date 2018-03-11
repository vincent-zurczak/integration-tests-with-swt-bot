/******************************************************************************
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * 		Vincent Zurczak - initial API and implementation
 *******************************************************************************/

package net.vzurczak.eclipse.tests.ide.suites;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import net.vzurczak.eclipse.tests.ide.tests.SvnMultiModuleTest;

/**
 * La suite de tests liés aux projets multi-modules sur SVN.
 * @author Vincent Zurczak - Linagora
 */
@RunWith(Suite.class)
@SuiteClasses({
    SvnMultiModuleTest.class
})
public class SvnMultiModulesTests {
	// nothing
}
