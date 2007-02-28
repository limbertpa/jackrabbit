/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.spi.rmi.common;

import org.apache.jackrabbit.name.Path;
import org.apache.jackrabbit.spi.IdFactory;
import org.apache.jackrabbit.spi.ItemId;
import org.apache.jackrabbit.spi.PropertyId;
import org.apache.jackrabbit.spi.NodeId;
import org.apache.jackrabbit.name.QName;
import org.apache.jackrabbit.name.MalformedPathException;

import java.io.Serializable;

/**
 * <code>SerializableIdFactory</code> implements an id factory with serializable
 * item ids.
 * TODO: copied from spi2dav, move common part to spi-commons.
 */
public class SerializableIdFactory implements IdFactory {

    private static final SerializableIdFactory INSTANCE = new SerializableIdFactory();

    private SerializableIdFactory() {};

    public static SerializableIdFactory getInstance() {
        return INSTANCE;
    }

    /**
     * {@inheritDoc}
     */
    public PropertyId createPropertyId(NodeId parentId, QName propertyName) {
        try {
            return new PropertyIdImpl(parentId, propertyName);
        } catch (MalformedPathException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     */
    public NodeId createNodeId(NodeId parentId, Path path) {
        try {
            return new NodeIdImpl(parentId, path);
        } catch (MalformedPathException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     */
    public NodeId createNodeId(String uniqueID, Path path) {
        return new NodeIdImpl(uniqueID, path);
    }

    /**
     * {@inheritDoc}
     */
    public NodeId createNodeId(String uniqueID) {
        return new NodeIdImpl(uniqueID);
    }

    //------------------------------------------------------< Inner classes >---
    private static abstract class ItemIdImpl implements ItemId, Serializable {

        private final String uniqueID;
        private final Path path;

        private transient int hashCode = 0;

        private ItemIdImpl(String uniqueID, Path path) {
            if (uniqueID == null && path == null) {
                throw new IllegalArgumentException("Only uniqueID or relative path might be null.");
            }
            this.uniqueID = uniqueID;
            this.path = path;
        }

        private ItemIdImpl(NodeId parentId, QName name) throws MalformedPathException {
            if (parentId == null || name == null) {
                throw new IllegalArgumentException("Invalid ItemIdImpl: parentId and name must not be null.");
            }
            this.uniqueID = parentId.getUniqueID();
            Path parentPath = parentId.getPath();
            if (parentPath != null) {
                this.path = Path.create(parentPath, name, true);
            } else {
                this.path = Path.create(name, Path.INDEX_UNDEFINED);
            }
        }

        public abstract boolean denotesNode();

        public String getUniqueID() {
            return uniqueID;
        }

        public Path getPath() {
            return path;
        }

        /**
         * ItemIdImpl objects are equal if the have the same uuid and relative path.
         *
         * @param obj
         * @return
         */
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj instanceof ItemId) {
                ItemId other = (ItemId) obj;
                return equals(other);
            }
            return false;
        }

        boolean equals(ItemId other) {
            return (uniqueID == null ? other.getUniqueID() == null : uniqueID.equals(other.getUniqueID()))
                && (path == null ? other.getPath() == null : path.equals(other.getPath()));
        }

        /**
         * Returns the hash code of the uuid and the path. The computed hash code
         * is memorized for better performance.
         *
         * @return hash code
         * @see Object#hashCode()
         */
        public int hashCode() {
            // since the ItemIdImpl is immutable, store the computed hash code value
            if (hashCode == 0) {
                hashCode = toString().hashCode();
            }
            return hashCode;
        }

        /**
         * Combination of uuid and relative path
         *
         * @return
         */
        public String toString() {
            StringBuffer b = new StringBuffer();
            if (uniqueID != null) {
                b.append(uniqueID);
            }
            if (path != null) {
                b.append(path.toString());
            }
            return b.toString();
        }
    }

    private static class NodeIdImpl extends ItemIdImpl implements NodeId {

        public NodeIdImpl(String uniqueID) {
            super(uniqueID, null);
        }

        public NodeIdImpl(String uniqueID, Path path) {
            super(uniqueID, path);
        }

        public NodeIdImpl(NodeId parentId, Path path) throws MalformedPathException {
            super(parentId.getUniqueID(), (parentId.getPath() != null) ? Path.create(parentId.getPath(), path, true) : path);
        }

        public boolean denotesNode() {
            return true;
        }

        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj instanceof NodeId) {
                return super.equals((NodeId)obj);
            }
            return false;
        }
    }

    private static class PropertyIdImpl extends ItemIdImpl implements PropertyId {

        private final NodeId parentId;

        private PropertyIdImpl(NodeId parentId, QName name) throws MalformedPathException {
            super(parentId, name);
            this.parentId = parentId;
        }

        public boolean denotesNode() {
            return false;
        }

        public NodeId getParentId() {
            return parentId;
        }

        public QName getQName() {
            return getPath().getNameElement().getName();
        }

        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj instanceof PropertyId) {
                return super.equals((PropertyId)obj);
            }
            return false;
        }
    }
}