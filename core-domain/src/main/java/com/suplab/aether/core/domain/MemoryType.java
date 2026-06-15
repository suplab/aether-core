package com.suplab.aether.core.domain;

/**
 * Taxonomy of personal memory that Aether Core manages.
 *
 * <ul>
 *   <li>EPISODIC   — specific events and experiences ("I attended that meeting on Monday")</li>
 *   <li>SEMANTIC   — factual knowledge and concepts ("Java uses garbage collection")</li>
 *   <li>PROCEDURAL — learned skills and processes ("how to deploy to Kubernetes")</li>
 *   <li>EMOTIONAL  — emotional associations and states ("felt anxious during Q4 planning")</li>
 * </ul>
 */
public enum MemoryType {
    EPISODIC,
    SEMANTIC,
    PROCEDURAL,
    EMOTIONAL
}
