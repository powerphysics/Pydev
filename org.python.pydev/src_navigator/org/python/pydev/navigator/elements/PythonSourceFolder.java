/*
 * Created on Oct 8, 2006
 * @author Fabio
 */
package org.python.pydev.navigator.elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.ui.IContributorResourceAdapter;

/**
 * This is the the model for a source folder that exists within a project.
 * 
 * @author Fabio
 */
public class PythonSourceFolder implements IWrappedResource, IAdaptable, IContributorResourceAdapter{

    public IContainer container;
    public Object parentElement;
    
    /**
     * Maps the 'actual objects' to their python counterparts
     */
    public Map<IResource, IWrappedResource> children = new HashMap<IResource, IWrappedResource>();
    
    /**
     * Maps from a wrapped resource (that must be a container) to its children
     */
    public Map<IResource, List<IWrappedResource>> childrenForContainer = new HashMap<IResource, List<IWrappedResource>>();

    protected PythonSourceFolder(Object parentElement, IContainer container) {
        this.parentElement = parentElement;
        this.container = container;
    }
    
    public PythonSourceFolder(Object parentElement, IFolder folder) {
        this(parentElement, (IContainer)folder);
//        System.out.println("Created PythonSourceFolder:"+this+" - "+folder+" parent:"+parentElement);
    }

	public Object getParentElement() {
		return parentElement;
	}

	public IResource getActualObject() {
		return container;
	}

	public PythonSourceFolder getSourceFolder() {
		return this;
	}
	
	public void addChild(IWrappedResource child){
		IResource actualObject = (IResource) child.getActualObject();
        children.put(actualObject, child);
        
        IContainer container = actualObject.getParent();
        Assert.isNotNull(container);
        List<IWrappedResource> l = childrenForContainer.get(container);
        if(l == null){
            l = new ArrayList<IWrappedResource>();
            childrenForContainer.put(container, l);
        }
        l.add(child);
	}
	
	public void removeChild(IResource actualObject){
	    //System.out.println("Removing child:"+actualObject);
        children.remove(actualObject);
        if(actualObject instanceof IContainer){
            List<IWrappedResource> l = childrenForContainer.get(actualObject);
            if(l != null){
                for (IWrappedResource resource : l) {
                    removeChild((IResource) resource.getActualObject());
                }
                childrenForContainer.remove(actualObject);
            }
        }
	}
	
	public Object getChild(IResource actualObject){
		IWrappedResource ret = children.get(actualObject);
		//System.out.println("Gotten child:"+ret+" for resource:"+actualObject);
        return ret;
	}
    
    
	
	public int getRank() {
	    return IWrappedResource.RANK_SOURCE_FOLDER;
	}
    

    public IResource getAdaptedResource(IAdaptable adaptable) {
        return (IResource) getActualObject();
    }

    @SuppressWarnings("unchecked")
    public Object getAdapter(Class adapter) {
        if(adapter == IContributorResourceAdapter.class){
            return this;
        }
        Object ret = ((IResource)this.getActualObject()).getAdapter(adapter);
        return ret;
    }

    
}