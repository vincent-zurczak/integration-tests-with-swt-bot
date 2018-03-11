/******************************************************************************
 * Copyright (c) 2010, 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * 		IBM Corporation - initial API and implementation
 *******************************************************************************/

package net.vzurczak.eclipse.tests.installer.setup;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.util.SafeRunnable;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.testing.TestableObject;

/**
 * A copy of E4Testable, but tailored for OOMPH.
 * <p>
 * E4Testable use dependency injection. This is not necessary
 * here since the Eclipse application lies in the same bundle.
 * </p>
 * <p>
 * We poll SWT widgets to decide when we can run the tests.
 * </p>
 */
public class OomphTestableObject extends TestableObject {

	private Display display;
	private boolean oldAutomatedMode;
	private boolean oldIgnoreErrors;


	/**
	 * Constructs a new testable object.
	 */
	public OomphTestableObject() {
		final ExecutorService executor = Executors.newSingleThreadExecutor();
		executor.submit( new UiPollingRunnable( executor, this ));
	}


	/**
	 * A runnable that polls the display to determine when tests can be run.
	 * @author Vincent Zurczak - Linagora
	 */
	private static class UiPollingRunnable implements Runnable {
		private static final AtomicBoolean INITIALIZED = new AtomicBoolean( false );

		final ExecutorService executor;
		final OomphTestableObject testableObject;

		/**
		 * Constructor.
		 */
		public UiPollingRunnable( ExecutorService executor, OomphTestableObject testableObject ) {
			this.executor = executor;
			this.testableObject = testableObject;
		}

		@Override
		public void run() {

			// Polling the UI must be done in the UI thread!
			// And this must be executed synchronously.
			Display.getDefault().syncExec( new Runnable() {
				@Override
				public void run() {

					Shell[] shells = Display.getDefault().getShells();
					if( shells.length > 0 ) {

						// FIXME: search org.eclipse.oomph.setup.internal.installer.InstallerDialog ???
						// Or check the shell's name ("Eclipse Installer")?

						INITIALIZED.set( true );
						UiPollingRunnable.this.testableObject.init( Display.getDefault());
					}
				}
			});

			// If the poll failed, reschedule another poll.
			// FIXME: we might consider using a delay (we would use a ScheduledThreadPool as our executor service).
			// Print traces have shown the polling is done only once, so this is probably not necessary.
			if( ! INITIALIZED.get())
				this.executor.submit( new UiPollingRunnable( this.executor, this.testableObject ));
			else
				this.executor.shutdown();
		}
	}


	/**
	 * Initializes the display and notifies all listeners that the tests can be run.
	 * @param display the display
	 */
	public void init(Display display) {

		Assert.isNotNull(display);
		this.display = display;
		if (getTestHarness() != null) {
			// Don't use a job, since tests often wait for all jobs to complete before proceeding

			Runnable runnable = new Runnable() {
				@Override
				public void run() {
					getTestHarness().runTests();
				};
			};

			new Thread(runnable, "WorkbenchTestable").start(); //$NON-NLS-1$
		}
	}


	// The following code, apart the "// VZ" sections, are copies of the E4TestableObject class.


	/**
	 * The <code>WorkbenchTestable</code> implementation of this
	 * <code>TestableObject</code> method ensures that the workbench has been
	 * set.
	 */
	@Override
	public void testingStarting() {
		this.oldAutomatedMode = ErrorDialog.AUTOMATED_MODE;
		ErrorDialog.AUTOMATED_MODE = true;
		this.oldIgnoreErrors = SafeRunnable.getIgnoreErrors();
		SafeRunnable.setIgnoreErrors(true);
	}


	/**
	 * The <code>WorkbenchTestable</code> implementation of this
	 * <code>TestableObject</code> method flushes the event queue, runs the test
	 * in a <code>syncExec</code>, then flushes the event queue again.
	 */
	@Override
	public void runTest(Runnable testRunnable) {
		this.display.syncExec(testRunnable);
	}


	/**
	 * The <code>WorkbenchTestable</code> implementation of this
	 * <code>TestableObject</code> method flushes the event queue, then closes
	 * the workbench.
	 */
	@Override
	public void testingFinished() {
		// Force events to be processed, and ensure the close is done in the UI thread
		// VZ
		Runnable closeRunnable = new Runnable() {
			@Override
			public void run() {

				for( Shell shell : Display.getDefault().getShells()) {
					if( ! shell.isDisposed())
						shell.close();
				}

			}
		};

		this.display.syncExec( closeRunnable );
		// VZ

		ErrorDialog.AUTOMATED_MODE = this.oldAutomatedMode;
		SafeRunnable.setIgnoreErrors(this.oldIgnoreErrors);
	}
}
