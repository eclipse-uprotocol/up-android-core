/*
 * Copyright (c) 2024 General Motors GTO LLC
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * SPDX-FileType: SOURCE
 * SPDX-FileCopyrightText: 2024 General Motors GTO LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.uprotocol.core.udiscovery.db;


import static org.eclipse.uprotocol.common.util.UStatusUtils.checkArgument;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkArgumentPositive;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkNotNull;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkStringNotEmpty;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.tag;
import static org.eclipse.uprotocol.core.udiscovery.internal.Utils.hasCharAt;
import static org.eclipse.uprotocol.core.udiscovery.internal.Utils.parseAuthority;
import static org.eclipse.uprotocol.core.udiscovery.internal.Utils.sanitizeUri;
import static org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery.SERVICE;

import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;

import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.core.udiscovery.v3.Node;
import org.eclipse.uprotocol.core.udiscovery.v3.NodeOrBuilder;
import org.eclipse.uprotocol.uri.serializer.LongUriSerializer;
import org.eclipse.uprotocol.v1.UAuthority;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UUri;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class DatabaseLoader {
    private static final String TAG = tag(SERVICE.getName());

    /**
     * @return Node
     * @fn internalFindNode
     * @brief Find a node with the given uri.
     * Node objects are immutable. Therefore, this function is useful when
     * searching for a node without modifying the tree.
     * @param[in] rootNode - the tree upon which to search
     * @param[in] uri - uri corresponding to the node marked for deletion
     */
    public static Node internalFindNode(Node rootNode, String uri) {
        Node node = rootNode;
        boolean isFound = false;
        while ((!isFound) && (node != null)) {
            if (node.getUri().equals(uri)) {
                isFound = true;
            } else {
                node = traverseNode(node, uri);
            }
        }
        return node;
    }

    /**
     * @return List<Node.Builder> path to the node
     * @fn FindPathToNode
     * @brief traverse the tree to build a list of nodes from the root to the node being searched.
     * @param[in] rootBld - the tree upon which to search
     * Builders are mutable, unlike Node objects. Therefore, this function
     * is useful when adding, deleting or otherwise modifying the tree.
     * @param[in] uri - uri corresponding to the node
     */
    public static List<Node.Builder> FindPathToNode(Node.Builder rootBld, String uri) {
        final ArrayList<Node.Builder> nodePath = new ArrayList<>();
        Node.Builder bld = rootBld;
        boolean isFound = false;
        while ((!isFound) && (bld != null)) {
            nodePath.add(bld);
            if (bld.getUri().equals(uri)) {
                isFound = true;
            } else {
                bld = traverseNode(bld, uri);
            }
        }
        if (!isFound) {
            nodePath.clear();
        }
        return nodePath;
    }

    /**
     * @fn DeleteNodeFromPath
     * @brief remove the last node in the list (child node) from the node before it (parent node)
     * @param[in] nodePath - a list of nodes from the root to the leaf
     */
    public void DeleteNodeFromPath(List<Node.Builder> nodePath) {
        final int rootNodeSize = nodePath.size();
        checkArgument(rootNodeSize >= 2,
                "[DeleteNodeFromPath] path must contain at least two nodes " + nodePath);
        final Node.Builder lastElement = nodePath.get(rootNodeSize - 1);
        final Node.Builder parent = nodePath.get(rootNodeSize - 2);
        for (int i = parent.getNodesCount() - 1; i >= 0; --i) {
            final Node.Builder child = parent.getNodesBuilder(i);
            if (child.equals(lastElement)) {
                parent.removeNodes(i);
            }
        }
    }

    /**
     * @return Node
     * @fn traverseNode
     * @brief Traverses the tree one level using Node objects
     * Node objects are immutable. Therefore, this function is useful when
     * searching for a node without modifying the tree.
     * @param[] nodeUri - uri of the desired node
     * parent  - the node to search
     */
    private static Node traverseNode(Node parent, String uri) {
        return parent.getNodesList().stream()
                .filter(child -> isSubUri(uri, child.getUri()))
                .findFirst()
                .orElse(null);
    }

    /**
     * @return Node.Builder
     * @fn traverseNode
     * @brief Traverses the tree one level using Builder objects
     * Builders are mutable, unlike Node objects. Therefore, this function
     * is useful when adding, deleting or otherwise modifying the tree.
     * @param[] parent - the node builder to search
     * @param[] uri - URI of the desired node
     */
    private static Node.Builder traverseNode(Node.Builder parent, String uri) {
        return parent.getNodesBuilderList().stream()
                .filter(child -> isSubUri(uri, child.getUri()))
                .findFirst()
                .orElse(null);
    }

    private static boolean isSubUri(@NonNull String uri, @NonNull String subUri) {
        if (uri.startsWith(subUri)) {
            final int length = subUri.length();
            return (uri.length() == length) ? true : hasCharAt(uri, length, '/');
        }
        return false;
    }

    /**
     * @return Node
     * @fn internalDeleteNode
     * @brief This function will search for and delete a given node with the
     * corresponding uri if one exists. If the node is not found, this
     * function will simply return the unmodified node.
     * @param[in] node - Parent node containing the node marked for deletion
     * @param[in] uri  - URI of the node to be deleted
     */
    public Node internalDeleteNode(Node node, String uri) {
        checkNotNull(node, "[internalDeleteNode] node is null");
        checkStringNotEmpty(uri, "[internalDeleteNode] uri is empty");
        final List<Node.Builder> nodePath = FindPathToNode(node.toBuilder(), uri);
        checkArgumentPositive(nodePath.size(), UCode.NOT_FOUND, "[deleteNode] could not find " + uri);
        DeleteNodeFromPath(nodePath);
        return nodePath.get(0).build();
    }

    /**
     * @return Node
     * @fn verifyNode
     * @brief Verifies the integrity of new nodes and sanitizes the uri of all children nodes,
     * for example, converting uppercase to lowercase, etc...
     * This implementation uses a stack to iterate over the tree to avoid recursion.
     * @param[] node - Node
     */
    public Node verifyNode(Node node) {
        checkNotNull(node, "[verifyNode] node is null");
        final Node.Builder rootBld = node.toBuilder();
        final Deque<Node.Builder> stack = new ArrayDeque<>();
        stack.push(rootBld);
        while (!stack.isEmpty()) {
            final Node.Builder bld = stack.pop();
            final boolean isValidType = !bld.getType().equals(Node.Type.UNSPECIFIED);
            checkArgument(isValidType, "[verifyNode] invalid node type " + bld.getType());
            final String uri = sanitizeUri(bld.getUri());
            bld.setUri(uri);
            verifyLineage(bld);
            stack.addAll(bld.getNodesBuilderList());
        }
        return rootBld.build();
    }

    /**
     * @return node tree with given depth
     * @fn copy
     * @brief Makes a copy of a node tree given a certain depth
     * @param[] node - node to be used as a base
     * @param[] depth - depth number to limit how far the node tree goes down to
     */
    public Node copy(Node node, int depth) {
        checkNotNull(node, "[copy] node is null");
        final Node.Builder rootBld = node.toBuilder();
        final Deque<Pair<Node.Builder, Integer>> stack = new ArrayDeque<>();
        Node.Builder bld = rootBld;
        stack.push(new Pair<>(bld, 0));
        while (!stack.isEmpty()) {
            final Pair<Node.Builder, Integer> currentNode = stack.pop();
            bld = currentNode.first;
            final int currentDepth = currentNode.second;
            if (currentDepth >= depth) {
                bld.clearNodes();
            }
            for (Node.Builder child : bld.getNodesBuilderList()) {
                stack.push(new Pair<>(child, currentDepth + 1));
            }
        }
        return rootBld.build();
    }

    /**
     * @return None
     * @return Node.Builder - reference to the newly inserted node as a builder
     * @fn commitNode
     * @brief Adds a new node to the hierarchy
     * If a node with the same URI exists, it will be overwritten
     * @param[] node - node to insert into the hierarchy
     * @param[] bld  - builder for the parent, which will adopt the node
     */
    public Node.Builder commitNode(Node.Builder bld, Node node) {
        checkNotNull(bld, "[commitNode] bld is null");
        checkNotNull(node, "[commitNode] node is null");
        final String nodeUri = node.getUri();
        for (int i = bld.getNodesCount() - 1; i >= 0; --i) {
            final Node child = bld.getNodes(i);
            if (child.getUri().equals(nodeUri)) {
                bld.removeNodes(i);
            }
        }
        bld.addNodes(node);
        return bld.getNodesBuilder(bld.getNodesCount() - 1);
    }

    /**
     * @return None
     * @fn verifyLineage
     * @brief Confirm whether each node is at the right level based on uri
     * @param[] bld - Builder
     */
    private void verifyLineage(Node.Builder parent) {
        checkNotNull(parent, "[verifyLineage] parent is null");
        final String parentUri = parent.getUri();
        final Set<String> uriList = new HashSet<>();
        for (Node child : parent.getNodesList()) {
            final String childUri = sanitizeUri(child.getUri());
            checkArgument(uriList.add(childUri), UCode.ALREADY_EXISTS,
                    "[verifyLineage] node already exists " + childUri);
            final boolean result = verifyParentChild(parentUri, childUri);
            checkArgument(result, "[verifyLineage] invalid hierarchy: " +
                    parent.getType() + " { " + parent.getUri() + " } --> " +
                    child.getType() + " { " + parentUri + " }");
        }
    }

    /**
     * @return true if uri's are found to be similar, false otherwise
     * @fn verifyParentChild
     * @brief Confirm whether parent and child uri's match.
     * The child uri shall contain the parent uri substring.
     * @param[] parent - parent node uri
     * @param[] child - child node uri
     */
    private boolean verifyParentChild(String parent, String child) {
        try {
            final List<String> parentList = splitUri(parent);
            final List<String> childList = splitUri(child);
            checkArgumentPositive(parentList.size(), "invalid parent uri " + parent);
            checkArgumentPositive(childList.size(), "invalid child uri " + child);
            if (parentList.size() == childList.size()) {
                return verifyDomainDevice(parent, child);
            }
            childList.remove(childList.size() - 1);
            return parentList.equals(childList);
        } catch (UStatusException e) {
            Log.e(TAG, join("verifyParentChild", e.getMessage()));
        }
        return false;
    }

    private List<String> splitUri(String uri) {
        final String[] strSplit = uri.split("/");
        final ArrayList<String> parts = new ArrayList<>(Arrays.asList(strSplit));
        parts.removeAll(List.of(""));
        return parts;
    }

    /**
     * @return true if successful, false otherwise
     * @fn verifyDomainDevice
     * @brief This function confirms whether the domain node uri matches the device node uri
     * The domain part of the device node uri shall be equal to the entire domain node uri
     * @param[] node - the node to reformat
     * @param[] authority - LDS authority
     */
    private boolean verifyDomainDevice(String parent, String child) {
        final LongUriSerializer lus = LongUriSerializer.instance();
        final UAuthority parentAuthority = lus.deserialize(parent).getAuthority();
        final UAuthority childAuthority = lus.deserialize(child).getAuthority();
        final String parentDomain = parentAuthority.getName();
        final String childDomain = parseAuthority(childAuthority).second;
        return parentDomain.equals(childDomain);
    }

    public List<UUri> extractUriFromNodeOrBuilder(List<NodeOrBuilder> listOfNodeOrBuilder) {
        ArrayList<UUri> uriList = new ArrayList<>();
        for (NodeOrBuilder node : listOfNodeOrBuilder) {
            uriList.add(LongUriSerializer.instance().deserialize(node.getUri()));
        }
        return uriList;
    }

    public void insert(List<List<Node.Builder>> list, List<Node.Builder> branch) {
        int idx = list.size();
        for (int i = 0; i < list.size(); i++) {
            if (branch.size() >= list.get(i).size()) {
                idx = i;
                break;
            }
        }
        list.add(idx, branch);
    }
}
