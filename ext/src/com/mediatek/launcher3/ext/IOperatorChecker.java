package com.mediatek.launcher3.ext;

public interface IOperatorChecker {
    /**
     *  Whether support app list edit and hide.
     *  
     * @return True for OP09 projects, else false.
     */
    boolean supportEditAndHideApps();
    
    /**
     * Whether support app list cycle sliding.
     * 
     * @return True for OP09 projects, else false.
     */
    boolean supportAppListCycleSliding();
}
