/*******************************************************************************
 * Copyright (c) 2015 Ketan Padegaonkar and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Ketan Padegaonkar - initial API and implementation
 *     Jérôme Joslet - Bug 460403
 *******************************************************************************/

package net.vzurczak.eclipse.tests.installer.setup;

import java.io.IOException;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.Platform;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.swtbot.eclipse.junit.headless.EclipseTestRunner;
import org.eclipse.ui.testing.ITestHarness;

/**
 * A custom Eclipse application that runs SWT Bot tests for OOMPH.
 * <p>
 * This is a copy of org.eclipse.swtbot.eclipse.junit.headless.UITestApplication
 * but tailored for OOMPH UI. The main differences are that we know exactly which
 * application SWT Bot must run within (that's the OOMPH installer) and we use a
 * custom testable object.
 * </p>
 *
 * @author Ketan Padegaonkar &lt;KetanPadegaonkar [at] gmail [dot] com&gt;
 * @author Jérôme Joslet
 * @author Vincent Zurczak - Linagora
 */
public class CustomSwtBotApplication implements IApplication, ITestHarness {

	private static final String APP_TO_RUN = "org.eclipse.oomph.setup.installer.application";

	private OomphTestableObject fTestableObject;
	private IApplication fApplication;


	/*
	 * (non-Javadoc)
	 * @see org.eclipse.equinox.app.IApplication
	 * #start(org.eclipse.equinox.app.IApplicationContext)
	 */
	@Override
	public Object start(IApplicationContext context) throws Exception {

		String[] args = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
		Object app = getApplication(args);
		Assert.isNotNull(app, "The application " + APP_TO_RUN + " could not be found.");

		this.fTestableObject = new OomphTestableObject();
		this.fTestableObject.setTestHarness(this);

		if (app instanceof IApplication) {
			this.fApplication = (IApplication) app;
			return this.fApplication.start(context);
		}

		throw new IllegalArgumentException("Could not execute application " + APP_TO_RUN);
	}


	/*
	 * (non-Javadoc)
	 * @see org.eclipse.equinox.app.IApplication#stop()
	 */
	@Override
	public void stop() {
		if (this.fApplication != null)
			this.fApplication.stop();
	}


	/**
	 * @return the application to run, or null if not even the default application is found.
	 */
	private Object getApplication(String[] args) throws CoreException {

		IExtension extension = Platform.getExtensionRegistry().getExtension(
				Platform.PI_RUNTIME,
				Platform.PT_APPLICATIONS,
				APP_TO_RUN);

		Assert.isNotNull(extension, "Could not find IExtension for application: " + APP_TO_RUN);

		// If the extension does not have the correct grammar, return null.
		// Otherwise, return the application object.
		IConfigurationElement[] elements = extension.getConfigurationElements();
		if (elements.length > 0) {
			Object runnable = elements[0].createExecutableExtension("run"); //$NON-NLS-1$
			if (runnable instanceof IApplication)
				return runnable;
		}

		return null;
	}


	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.testing.ITestHarness#runTests()
	 */
	@Override
	public void runTests() {

		// Platform.getCommandLineArgs() includes environment properties.
		// All the parameters comes from the ANT script. Some of these parameters must be passed
		// to the JVM. So, rely on the ANT script and do not try to update them here.
		// I already tried, unsuccessfully. ;)

		this.fTestableObject.testingStarting();
		try {
			EclipseTestRunner.run(Platform.getCommandLineArgs());

		} catch (IOException e) {
			e.printStackTrace();
		}

		this.fTestableObject.testingFinished();
	}
}
