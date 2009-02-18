package com.python.pydev.analysis.builder;

import org.python.pydev.core.IPythonNature;
import org.python.pydev.core.log.Log;
import org.python.pydev.logging.DebugSettings;

import com.python.pydev.analysis.additionalinfo.AbstractAdditionalDependencyInfo;
import com.python.pydev.analysis.additionalinfo.AdditionalProjectInterpreterInfo;

/**
 * This class is used to do analysis on a thread, just to remove the actual info.
 * 
 * @author Fabio
 */
public class AnalysisBuilderRunnableForRemove extends AbstractAnalysisBuilderRunnable{
    
    
    /**
     * @param oldAnalysisBuilderThread This is an existing runnable that was already analyzing things... we must wait for it
     * to finish to start it again.
     * 
     * @param module: this is a callback that'll be called with a boolean that should return the IModule to be used in the
     * analysis.
     * The parameter is FULL_MODULE or DEFINITIONS_MODULE
     */
    /*Default*/ AnalysisBuilderRunnableForRemove(String moduleName, IPythonNature nature, boolean isFullBuild, 
            IAnalysisBuilderRunnable oldAnalysisBuilderThread, boolean forceAnalysis, int analysisCause, long documentTime,
            KeyForAnalysisRunnable key) {
        super(isFullBuild, moduleName, forceAnalysis, analysisCause, oldAnalysisBuilderThread, nature, documentTime, key);
    }


    public void doAnalysis(){
        if(DebugSettings.DEBUG_ANALYSIS_REQUESTS){
            Log.toLogFile(this, "Removing additional info from: "+moduleName);
        }
        removeInfoForModule(moduleName, nature, isFullBuild);
    }


    /**
     * @param moduleName this is the module name
     * @param nature this is the nature
     */
    public static void removeInfoForModule(String moduleName, IPythonNature nature, boolean isFullBuild) {
        if(moduleName != null && nature != null){
            AbstractAdditionalDependencyInfo info = AdditionalProjectInterpreterInfo.
                getAdditionalInfoForProject(nature);
            
            boolean generateDelta;
            if(isFullBuild){
                generateDelta = false;
            }else{
                generateDelta = true;
            }
            info.removeInfoFromModule(moduleName, generateDelta);
        }else{
            if(DebugSettings.DEBUG_ANALYSIS_REQUESTS){
                Log.toLogFile("Unable to remove info. name: "+moduleName+" or nature:"+nature+" is null.", AnalysisBuilderRunnableForRemove.class);
            }
        }
    }
}
