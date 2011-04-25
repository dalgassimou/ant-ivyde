/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.ivyde.eclipse.resolvevisualizer.model;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.IvyNode;
import org.apache.ivy.core.resolve.IvyNodeCallers.Caller;

public class IvyNodeElementAdapter {
    /**
     * Adapt all dependencies and evictions from the ResolveReport.
     * 
     * @param report
     * @return the root node adapted from the ResolveReport
     */
    public static IvyNodeElement adapt(ResolveReport report) {
        Map/* <ModuleRevisionId, IvyNodeElement> */resolvedNodes = new HashMap/* <ModuleRevisionId, IvyNodeElement> */();

        IvyNodeElement root = new IvyNodeElement();
        root.setModuleRevisionId(report.getModuleDescriptor().getModuleRevisionId());
        resolvedNodes.put(report.getModuleDescriptor().getModuleRevisionId(), root);

        List/* <IvyNode> */dependencies = report.getDependencies();

        // First pass - build the map of resolved nodes by revision id
        for (Iterator iter = dependencies.iterator(); iter.hasNext();) {
            IvyNode node = (IvyNode) iter.next();
            if (node.getAllEvictingNodes() != null) {
                // Nodes that are evicted as a result of conf inheritance still appear
                // as dependencies, but with eviction data. They also appear as evictions.
                // We map them as evictions rather than dependencies.
                continue;
            }
            IvyNodeElement nodeElement = new IvyNodeElement();
            nodeElement.setModuleRevisionId(node.getResolvedId());
            resolvedNodes.put(node.getResolvedId(), nodeElement);
        }

        // Second pass - establish relationships between the resolved nodes
        for (Iterator iter = dependencies.iterator(); iter.hasNext();) {
            IvyNode node = (IvyNode) iter.next();
            if (node.getAllEvictingNodes() != null) {
                continue; // see note above
            }

            IvyNodeElement nodeElement = (IvyNodeElement) resolvedNodes.get(node.getResolvedId());
            Caller[] callers = node.getAllRealCallers();
            for (int i = 0; i < callers.length; i++) {
                IvyNodeElement caller = (IvyNodeElement) resolvedNodes.get(callers[i].getModuleRevisionId());
                if (caller != null) {
                    nodeElement.addCaller(caller);
                    nodeElement.setCallerConfigurations(caller, callers[i].getCallerConfigurations());
                }
            }
        }

        IvyNode[] evictions = report.getEvictedNodes();
        for (int i = 0; i < evictions.length; i++) {
            IvyNode eviction = evictions[i];
            IvyNodeElement evictionElement = new IvyNodeElement();
            evictionElement.setModuleRevisionId(eviction.getResolvedId());
            evictionElement.setEvicted(true);

            Caller[] callers = eviction.getAllCallers();
            for (int j = 0; j < callers.length; j++) {
                IvyNodeElement caller = (IvyNodeElement) resolvedNodes.get(callers[j].getModuleRevisionId());
                if (caller != null) {
                    evictionElement.addCaller(caller);
                    evictionElement.setCallerConfigurations(caller, callers[j].getCallerConfigurations());
                }
            }
        }

        // Recursively set depth starting at root
        root.setDepth(0);
        findConflictsBeneathNode(root);

        return root;
    }

    /**
     * Derives configuration conflicts that exist between node and all of its descendant dependencies.
     * 
     * @param node
     */
    private static void findConflictsBeneathNode(IvyNodeElement node) {
        // Derive conflicts
        Map/* <ModuleId, Collection<IvyNodeElement>> */moduleRevisionMap = new HashMap/*
                                                                                       * <ModuleId,
                                                                                       * Collection<IvyNodeElement>>
                                                                                       */();
        IvyNodeElement[] deepDependencies = node.getDeepDependencies();
        for (int i = 0; i < deepDependencies.length; i++) {
            if (deepDependencies[i].isEvicted())
                continue;

            ModuleId moduleId = deepDependencies[i].getModuleRevisionId().getModuleId();
            if (moduleRevisionMap.containsKey(moduleId)) {
                Collection/* <IvyNodeElement> */conflicts = (Collection/* <IvyNodeElement> */) moduleRevisionMap
                        .get(moduleId);
                conflicts.add(deepDependencies[i]);
                for (Iterator iter = conflicts.iterator(); iter.hasNext();) {
                    IvyNodeElement conflict = (IvyNodeElement) iter.next();
                    conflict.setConflicts(conflicts);
                }
            } else {
                List/* <IvyNodeElement> */immutableMatchingSet = Arrays
                        .asList(new IvyNodeElement[] { deepDependencies[i] });
                moduleRevisionMap.put(moduleId, new HashSet(immutableMatchingSet));
            }
        }
    }
}
