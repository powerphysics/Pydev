/**
 * Copyright (c) 2005-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Eclipse Public License (EPL).
 * Please see the license.txt included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
package org.python.pydev.django.ui.wizards.project;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.wizard.IWizardPage;
import org.python.pydev.core.ICodeCompletionASTManager;
import org.python.pydev.core.REF;
import org.python.pydev.core.Tuple;
import org.python.pydev.core.callbacks.ICallback;
import org.python.pydev.core.callbacks.ICallback0;
import org.python.pydev.core.uiutils.RunInUiThread;
import org.python.pydev.django.DjangoPlugin;
import org.python.pydev.django.launching.DjangoConstants;
import org.python.pydev.django.nature.DjangoNature;
import org.python.pydev.django.ui.wizards.project.DjangoSettingsPage.DjangoSettings;
import org.python.pydev.editor.actions.PyAction;
import org.python.pydev.plugin.PyStructureConfigHelpers;
import org.python.pydev.plugin.PydevPlugin;
import org.python.pydev.plugin.nature.PythonNature;
import org.python.pydev.runners.UniversalRunner;
import org.python.pydev.runners.UniversalRunner.AbstractRunner;
import org.python.pydev.ui.wizards.project.IWizardNewProjectNameAndLocationPage;
import org.python.pydev.ui.wizards.project.PythonProjectWizard;


/**
 * Creation of a Django project
 * 
 * @author Mikko Ohtamaa
 * @author Leo Soto
 * @author Fabio Zadrozny
 */
public class DjangoProjectWizard extends PythonProjectWizard {

	public static final String WIZARD_ID = "org.python.pydev.ui.wizards.project.DjangoProjectWizard";

	protected DjangoSettingsPage settingsPage;

	protected static final String RUN_DJANGO_ADMIN =
		"from django.core import management\n" +
		"management.execute_from_command_line()";

	public DjangoProjectWizard() {
		super();
		settingsPage = createDjangoSettingsPage(new ICallback0<IWizardNewProjectNameAndLocationPage>() {
			public IWizardNewProjectNameAndLocationPage call() {
				return projectPage;
			}
		});
	}

	@Override
    protected IWizardNewProjectNameAndLocationPage createProjectPage(){
        return new DjangoNewProjectPage("Setting project properties");
    }

    protected DjangoSettingsPage createDjangoSettingsPage(ICallback0<IWizardNewProjectNameAndLocationPage> projectPage)  {
    	return new DjangoSettingsPage("Django Settings", projectPage);
    }


    /**
     * Add wizard pages to the instance
     *
     * @see org.eclipse.jface.wizard.IWizard#addPages()
     */
    @Override
    public void addPages() {
    	super.addPages();
    	addPage(settingsPage);
    }

    @Override
    protected void createAndConfigProject(
    		IProject projectHandle,
    		IProjectDescription description, 
    		String projectType, 
    		String projectInterpreter,
    		IProgressMonitor monitor, 
    		Object ... additionalArgsToConfigProject) throws CoreException {

    	Assert.isTrue(additionalArgsToConfigProject.length == 1);
        final DjangoSettings djSettings = (DjangoSettings) additionalArgsToConfigProject[0];

        
        final int sourceFolderConfigurationStyle = projectPage.getSourceFolderConfigurationStyle();
        ICallback<List<IContainer>, IProject> getSourceFolderHandlesCallback = new ICallback<List<IContainer>, IProject>(){
            
            public List<IContainer> call(IProject projectHandle) {
                ArrayList<IContainer> ret;
                switch(sourceFolderConfigurationStyle){
                    
                    case IWizardNewProjectNameAndLocationPage.PYDEV_NEW_PROJECT_CREATE_PROJECT_AS_SRC_FOLDER:
                        //if the user hasn't selected to create a source folder, use the project itself for that.
                        ret = new ArrayList<IContainer>();
                        ret.add(projectHandle);
                        return ret;
                        
                    case IWizardNewProjectNameAndLocationPage.PYDEV_NEW_PROJECT_NO_PYTHONPATH:
                        return new ArrayList<IContainer>();
                    
                    default:
                        IContainer folder = projectHandle.getFolder("src");
                        ret = new ArrayList<IContainer>();
                        ret.add(folder);
                        return ret;
                }
            }
        };
        
        ICallback<Map<String, String>, IProject> getVariableSubstitutionCallback = new ICallback<Map<String, String>, IProject>(){
            
            public Map<String, String> call(IProject projectHandle){
                Map<String, String> variableSubstitution = new HashMap<String, String>();
                String manageLocation = projectHandle.getName()+"/manage.py";
                
                switch(sourceFolderConfigurationStyle){
                    case IWizardNewProjectNameAndLocationPage.PYDEV_NEW_PROJECT_CREATE_PROJECT_AS_SRC_FOLDER:
                    case IWizardNewProjectNameAndLocationPage.PYDEV_NEW_PROJECT_NO_PYTHONPATH:
                        break;
                    default:
                        manageLocation = "src/"+manageLocation;
                }
                
                variableSubstitution.put(DjangoConstants.DJANGO_MANAGE_VARIABLE, manageLocation);
				return variableSubstitution; 
            }
        };
        
        PyStructureConfigHelpers.createPydevProject(
                description, projectHandle, monitor, projectType, projectInterpreter, getSourceFolderHandlesCallback, null, getVariableSubstitutionCallback);
        
        //The django nature is added only so that we can identify whether we should show django actions.
        DjangoNature.addNature(projectHandle, null);
        
        try {
            if (monitor.isCanceled()){
                throw new OperationCanceledException();
            }
            PythonNature nature = PythonNature.getPythonNature(projectHandle);
            Assert.isNotNull(nature);
            ICodeCompletionASTManager astManager = nature.getAstManager();
            Object sync = new Object();
            //Wait up to 10 seconds for it to be restored
            for (int i = 0; i < 100 && astManager == null; i++) {
				synchronized (sync) {
					try {
						sync.wait(100);
					} catch (InterruptedException e) {
					}
				}
				astManager = nature.getAstManager();
			}

            if(astManager == null){
            	throw new RuntimeException(
            			"Error creating Django project. ASTManager not available after 10 seconds.\n" +
            			"Please report this bug at the sourceforge tracker.");
            }
            AbstractRunner runner = UniversalRunner.getRunner(nature);
            
            IContainer projectContainer;
            
            switch(sourceFolderConfigurationStyle){
                case IWizardNewProjectNameAndLocationPage.PYDEV_NEW_PROJECT_CREATE_PROJECT_AS_SRC_FOLDER:
                case IWizardNewProjectNameAndLocationPage.PYDEV_NEW_PROJECT_NO_PYTHONPATH:
                    projectContainer = projectHandle;
                    break;
                default:
                    projectContainer = projectHandle.getFolder("src");
            }
            
			Tuple<String, String> output = runner.runCodeAndGetOutput(
				RUN_DJANGO_ADMIN, 
				new String[]{
						"startproject", 
						projectHandle.getName()
				}, 
				projectContainer.getLocation().toFile(), 
				new NullProgressMonitor()
			);
			
			if(output.o2.indexOf("ImportError: no module named django") != -1){
				RunInUiThread.async(new Runnable() {
					
					public void run() {
						MessageDialog.openError(
								PyAction.getShell(), 
								"Unable to create project.", 
								"Unable to create project because the selected interpreter does not have django."
						);
					}
				});
				projectHandle.delete(true, null);
				return;
			}
			
            IFile settingsFile = projectContainer.getFile(new Path(
            		projectHandle.getName() + "/settings.py"));

            settingsFile.refreshLocal(IResource.DEPTH_ZERO, null);
            IDocument docFromResource = REF.getDocFromResource(settingsFile);
            if(docFromResource == null){
            	throw new RuntimeException(
            			"Error creating Django project.\n" +
            			"settings.py file not created.\n" +
            			"Stdout: "+output.o1+"\n"+
            			"Stderr: "+output.o2);
            }

			String settings = docFromResource.get();
            if (djSettings.djangoVersion.contains("1.2")){
            	settings = settings.replaceFirst(
            			"'ENGINE': 'django.db.backends.'",
            			"'ENGINE': 'django.db.backends." + djSettings.databaseEngine + "'");
                settings = settings.replaceFirst(
                		"'NAME': ''",
                		"'NAME': '" + djSettings.databaseName + "'");
                settings = settings.replaceFirst(
                		"'HOST': ''",
                		"'HOST': '" + djSettings.databaseHost + "'");
                settings = settings.replaceFirst(
                		"'PORT': ''",
                		"'PORT': '" + djSettings.databasePort + "'");
                settings = settings.replaceFirst(
                		"'USER': ''",
                		"'USER': '" + djSettings.databaseUser + "'");
                settings = settings.replaceFirst(
                		"'PASSWORD': ''",
                		"'PASSWORD': '" + djSettings.databasePassword + "'");
            } else {
            	settings = settings.replaceFirst(
            			"DATABASE_ENGINE = ''",
            			"DATABASE_ENGINE = '" + djSettings.databaseEngine + "'");
                settings = settings.replaceFirst(
                		"DATABASE_NAME = ''",
                		"DATABASE_NAME = '" + djSettings.databaseName + "'");
                settings = settings.replaceFirst(
                		"DATABASE_HOST = ''",
                		"DATABASE_HOST = '" + djSettings.databaseHost + "'");
                settings = settings.replaceFirst(
                		"DATABASE_PORT = ''",
                		"DATABASE_PORT = '" + djSettings.databasePort + "'");
                settings = settings.replaceFirst(
                		"DATABASE_USER = ''",
                		"DATABASE_USER = '" + djSettings.databaseUser + "'");
                settings = settings.replaceFirst(
                		"DATABASE_PASSWORD = ''",
                		"DATABASE_PASSWORD = '" + djSettings.databasePassword + "'");
            }
            
            
            settingsFile.setContents(new ByteArrayInputStream(settings.getBytes()), 0, monitor);

        } finally {
            monitor.done();
        }
    }


    /**
     * Creates a new project resource with the entered name.
     *
     * @return the created project resource, or <code>null</code> if the project was not created
     */
    @Override
    protected IProject createNewProject(final Object ... additionalArgsToConfigProject) {
    	if(additionalArgsToConfigProject != null && additionalArgsToConfigProject.length > 0){
    		throw new RuntimeException("Did not expect to receive arguments here.");
    	}
        final DjangoSettings djSettings = settingsPage.getSettings();
        return super.createNewProject(djSettings);
    }

    /**
     * Set Django logo to top bar
     */
    protected void initializeDefaultPageImageDescriptor() {
        ImageDescriptor desc = PydevPlugin.imageDescriptorFromPlugin(DjangoPlugin.getPluginID(), "icons/django_logo.png");//$NON-NLS-1$
        setDefaultPageImageDescriptor(desc);
    }
    
    @Override
    public boolean canFinish() {
        IWizardPage currentPage = this.getContainer().getCurrentPage();
        return currentPage == this.settingsPage; //can only finish at the last page!
    }
}
