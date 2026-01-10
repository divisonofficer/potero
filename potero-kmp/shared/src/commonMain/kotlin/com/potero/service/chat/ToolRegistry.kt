package com.potero.service.chat

import com.potero.service.chat.tools.ChatTool
import com.potero.service.chat.tools.ToolDefinition

/**
 * Registry for managing available chat tools.
 *
 * Responsibilities:
 * - Register tools
 * - Retrieve tool by name
 * - Get available tools (filtered by context)
 * - Provide tool definitions for prompt generation
 */
class ToolRegistry {
    private val tools = mutableMapOf<String, ChatTool>()

    /**
     * Register a chat tool.
     *
     * @param tool Tool to register
     * @throws IllegalArgumentException if tool with same name already exists
     */
    fun register(tool: ChatTool) {
        val name = tool.definition.name
        if (name in tools) {
            throw IllegalArgumentException("Tool '$name' is already registered")
        }
        tools[name] = tool
        println("[ToolRegistry] Registered tool: $name")
    }

    /**
     * Get tool by name.
     *
     * @param name Tool name
     * @return Tool instance or null if not found
     */
    fun getTool(name: String): ChatTool? {
        return tools[name]
    }

    /**
     * Get all registered tools.
     *
     * @return Map of tool name to tool instance
     */
    fun getAllTools(): Map<String, ChatTool> {
        return tools.toMap()
    }

    /**
     * Get available tool definitions filtered by context.
     *
     * If hasPaperContext is false, tools that require paper context are filtered out.
     *
     * @param hasPaperContext Whether current context has a paper (paperId)
     * @return List of tool definitions available in current context
     */
    fun getAvailableTools(hasPaperContext: Boolean): List<ToolDefinition> {
        return tools.values
            .filter { !it.definition.requiresPaper || hasPaperContext }
            .map { it.definition }
    }

    /**
     * Get count of registered tools.
     */
    fun getToolCount(): Int {
        return tools.size
    }

    /**
     * Check if a tool is registered.
     *
     * @param name Tool name
     * @return True if tool is registered
     */
    fun hasTo(name: String): Boolean {
        return name in tools
    }

    /**
     * Unregister a tool.
     *
     * @param name Tool name
     * @return True if tool was unregistered, false if not found
     */
    fun unregister(name: String): Boolean {
        val removed = tools.remove(name)
        if (removed != null) {
            println("[ToolRegistry] Unregistered tool: $name")
            return true
        }
        return false
    }

    /**
     * Clear all registered tools.
     */
    fun clear() {
        tools.clear()
        println("[ToolRegistry] Cleared all tools")
    }

    /**
     * Get tool names by category.
     *
     * @param requiresPaper If true, return only tools that require paper context.
     *                      If false, return only tools that don't require paper context.
     *                      If null, return all tools.
     * @return List of tool names
     */
    fun getToolNames(requiresPaper: Boolean? = null): List<String> {
        return if (requiresPaper == null) {
            tools.keys.toList()
        } else {
            tools.values
                .filter { it.definition.requiresPaper == requiresPaper }
                .map { it.definition.name }
        }
    }
}
