// IModelContextApp.aidl
package com.kyungrae.android.modelcontext;

import com.kyungrae.android.modelcontext.ToolInfo;
import com.kyungrae.android.modelcontext.ResourceInfo;
import com.kyungrae.android.modelcontext.ContentItem;

/**
 * Enhanced MCP-compatible interface for Android app services
 * This interface is designed to provide MCP-like functionality using AIDL
 */
interface IModelContextApp {
    /**
     * Basic service information
     */
    String getServiceType();
    String getServiceVersion();

    /**
     * Basic calculation capability (for backward compatibility)
     */
    String calculate(String value);

    /**
     * Tools interface - MCP-like tools functionality
     */
    List<ToolInfo> listTools();
    List<ContentItem> callTool(String name, String jsonArguments);

    /**
     * Resources interface - MCP-like resources functionality
     */
    List<ResourceInfo> listResources();
    List<ContentItem> readResource(String uri);

    /**
     * Advanced capabilities checking
     */
    boolean hasCapability(String capability);
}