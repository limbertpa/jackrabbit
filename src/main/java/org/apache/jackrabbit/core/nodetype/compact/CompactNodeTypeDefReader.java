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
package org.apache.jackrabbit.core.nodetype.compact;

import org.apache.jackrabbit.core.nodetype.InvalidConstraintException;
import org.apache.jackrabbit.core.nodetype.ItemDef;
import org.apache.jackrabbit.core.nodetype.NodeDef;
import org.apache.jackrabbit.core.nodetype.NodeDefImpl;
import org.apache.jackrabbit.core.nodetype.NodeTypeDef;
import org.apache.jackrabbit.core.nodetype.PropDef;
import org.apache.jackrabbit.core.nodetype.PropDefImpl;
import org.apache.jackrabbit.core.nodetype.ValueConstraint;
import org.apache.jackrabbit.core.value.InternalValue;
import org.apache.jackrabbit.name.IllegalNameException;
import org.apache.jackrabbit.name.NoPrefixDeclaredException;
import org.apache.jackrabbit.name.QName;
import org.apache.jackrabbit.name.UnknownPrefixException;
import org.apache.jackrabbit.name.NameFormat;
import org.apache.jackrabbit.util.name.NamespaceMapping;
import org.apache.jackrabbit.value.ValueHelper;
import org.apache.jackrabbit.value.ValueFactoryImpl;

import javax.jcr.NamespaceException;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFormatException;
import javax.jcr.version.OnParentVersionAction;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * CompactNodeTypeDefReader. Parses node type definitions written in the compact
 * node type definition format and returns a list of NodeTypeDef objects that
 * can then be used to register node types.
 * <p/>
 * The BNF grammar of the compact node type definition:<br>
 * <pre>
 * cnd ::= {ns_mapping | node_type_def}
 *
 * ns_mapping ::= "&lt;" prefix "=" namespace "&gt;"
 *
 * prefix ::= string
 *
 * namespace ::= string
 *
 * node_type_def ::= node_type_name [super_types] [options] {property_def | node_def}
 *
 * node_type_name ::= "[" string "]"
 *
 * super_types ::= "&gt;" string_list
 *
 * options ::= orderable_opt | mixin_opt | orderable_opt mixin_opt | mixin_opt orderable_opt
 *
 * orderable_opt ::= "orderable" | "ord" | "o"
 *
 * mixin_opt ::= "mixin" | "mix" | "m"
 *
 * property_def ::= "-" property_name [property_type_decl] [default_values] [attributes] [value_constraints]
 *
 * property_name ::= string
 *
 * property_type_decl ::= "(" property_type ")"
 *
 * property_type ::= "STRING" | "String |"string" |
 *                   "BINARY" | "Binary" | "binary" |
 *                   "LONG" | "Long" | "long" |
 *                   "DOUBLE" | "Double" | "double" |
 *                   "BOOLEAN" | "Boolean" | "boolean" |
 *                   "DATE" | "Date" | "date" |
 *                   "NAME | "Name | "name |
 *                   "PATH" | "Path" | "path" |
 *                   "REFERENCE" | "Reference" | "reference" |
 *                   "UNDEFINED" | "Undefined" | "undefined" | "*"
 *
 *
 * default_values ::= "=" string_list
 *
 * value_constraints ::= "&lt;" string_list
 *
 * node_def ::= "+" node_name [required_types] [default_type] [attributes]
 *
 * node_name ::= string
 *
 * required_types ::= "(" string_list ")"
 *
 * default_type ::= "=" string
 *
 * attributes ::= "primary" | "pri" | "!" |
 *                "autocreated" | "aut" | "a" |
 *                "mandatory" | "man" | "m" |
 *                "protected" | "pro" | "p" |
 *                "multiple" | "mul" | "*" |
 *                "COPY" | "Copy" | "copy" |
 *                "VERSION" | "Version" | "version" |
 *                "INITIALIZE" | "Initialize" | "initialize" |
 *                "COMPUTE" | "Compute" | "compute" |
 *                "IGNORE" | "Ignore" | "ignore" |
 *                "ABORT" | "Abort" | "abort"
 *
 * string_list ::= string {"," string}
 *
 * string ::= quoted_string | unquoted_string
 *
 * quoted_string :: = "'" unquoted_string "'"
 *
 * unquoted_string ::= [A-Za-z0-9:_]+
 * </pre>
 */
public class CompactNodeTypeDefReader {

    /**
     * the list of parsed nodetype defs
     */
    private List nodeTypeDefs = new LinkedList();

    /**
     * the current namespace mapping
     */
    private NamespaceMapping nsMapping;

    /**
     * the underlying lexer
     */
    private Lexer lexer;

    /**
     * the current token
     */
    private String currentToken;

    /**
     * Creates a new CND reader.
     *
     * @param r
     * @throws ParseException
     */
    public CompactNodeTypeDefReader(Reader r, String systemId) throws ParseException {
        this(r, systemId, new NamespaceMapping());
    }


    /**
     * Creates a new CND reader.
     *
     * @param r
     * @throws ParseException
     */
    public CompactNodeTypeDefReader(Reader r, String systemId, NamespaceMapping mapping)
            throws ParseException {
        lexer = new Lexer(r, systemId);
        this.nsMapping = mapping;
        nextToken();
        parse();
    }

    /**
     * Returns the list of parsed nodetype definitions.
     *
     * @return a List of NodeTypeDef objects
     */
    public List getNodeTypeDefs() {
        return nodeTypeDefs;
    }

    /**
     * Returns the namespace mapping.
     *
     * @return a NamespaceMapping object.
     */
    public NamespaceMapping getNamespaceMapping() {
        return nsMapping;
    }

    /**
     * Parses the definition
     *
     * @throws ParseException
     */
    private void parse() throws ParseException {
        while (!currentTokenEquals(Lexer.EOF)) {
            if (doNameSpace()) {
                continue;
            }
            NodeTypeDef ntd = new NodeTypeDef();
            ntd.setOrderableChildNodes(false);
            ntd.setMixin(false);
            ntd.setPrimaryItemName(null);
            doNodeTypeName(ntd);
            doSuperTypes(ntd);
            doOptions(ntd);
            doItemDefs(ntd);
            nodeTypeDefs.add(ntd);
        }
    }

    /**
     * processes the namespace declaration
     *
     * @return
     * @throws ParseException
     */
    private boolean doNameSpace() throws ParseException {
        if (!currentTokenEquals('<')) {
            return false;
        }
        nextToken();
        String prefix = currentToken;
        nextToken();
        if (!currentTokenEquals('=')) {
            lexer.fail("Missing = in namespace decl.");
        }
        nextToken();
        String uri = currentToken;
        nextToken();
        if (!currentTokenEquals('>')) {
            lexer.fail("Missing > in namespace decl.");
        }
        try {
            nsMapping.setMapping(prefix, uri);
        } catch (NamespaceException e) {
            // ignore
        }
        nextToken();
        return true;
    }

    /**
     * processes the nodetype name
     *
     * @param ntd
     * @throws ParseException
     */
    private void doNodeTypeName(NodeTypeDef ntd) throws ParseException {
        if (!currentTokenEquals(Lexer.BEGIN_NODE_TYPE_NAME)) {
            lexer.fail("Missing '" + Lexer.BEGIN_NODE_TYPE_NAME + "' delimiter for beginning of node type name");
        }
        nextToken();
        ntd.setName(toQName(currentToken));

        nextToken();
        if (!currentTokenEquals(Lexer.END_NODE_TYPE_NAME)) {
            lexer.fail("Missing '" + Lexer.END_NODE_TYPE_NAME + "' delimiter for end of node type name, found " + currentToken);
        }
        nextToken();
    }

    /**
     * processes the superclasses
     *
     * @param ntd
     * @throws ParseException
     */
    private void doSuperTypes(NodeTypeDef ntd) throws ParseException {
        // a set would be nicer here, in case someone defines a supertype twice.
        // but due to issue [JCR-333], the resulting node type definition is
        // not symmetric anymore and the tests will fail.
        ArrayList supertypes = new ArrayList();
        if (!currentTokenEquals(Lexer.EXTENDS)) {
            return;
        }
        do {
            nextToken();
            supertypes.add(toQName(currentToken));
            nextToken();
        } while (currentTokenEquals(Lexer.LIST_DELIMITER));
        ntd.setSupertypes((QName[]) supertypes.toArray(new QName[0]));
    }

    /**
     * processes the options
     *
     * @param ntd
     * @throws ParseException
     */
    private void doOptions(NodeTypeDef ntd) throws ParseException {
        if (currentTokenEquals(Lexer.ORDERABLE)) {
            ntd.setOrderableChildNodes(true);
            nextToken();
            if (currentTokenEquals(Lexer.MIXIN)) {
                ntd.setMixin(true);
                nextToken();
            }
        } else if (currentTokenEquals(Lexer.MIXIN)) {
            ntd.setMixin(true);
            nextToken();
            if (currentTokenEquals(Lexer.ORDERABLE)) {
                ntd.setMixin(true);
                nextToken();
            }
        }
    }

    /**
     * processes the item definitions
     *
     * @param ntd
     * @throws ParseException
     */
    private void doItemDefs(NodeTypeDef ntd) throws ParseException {
        List propertyDefinitions = new ArrayList();
        List nodeDefinitions = new ArrayList();
        while (currentTokenEquals(Lexer.PROPERTY_DEFINITION) || currentTokenEquals(Lexer.CHILD_NODE_DEFINITION)) {
            if (currentTokenEquals(Lexer.PROPERTY_DEFINITION)) {
                PropDefImpl pdi = new PropDefImpl();

                pdi.setAutoCreated(false);
                pdi.setDeclaringNodeType(ntd.getName());
                pdi.setDefaultValues(null);
                pdi.setMandatory(false);
                pdi.setMultiple(false);
                pdi.setOnParentVersion(OnParentVersionAction.COPY);
                pdi.setProtected(false);
                pdi.setRequiredType(PropertyType.STRING);
                pdi.setValueConstraints(null);

                nextToken();
                doPropertyDefinition(pdi, ntd);
                propertyDefinitions.add(pdi);

            } else if (currentTokenEquals(Lexer.CHILD_NODE_DEFINITION)) {
                NodeDefImpl ndi = new NodeDefImpl();

                ndi.setAllowsSameNameSiblings(false);
                ndi.setAutoCreated(false);
                ndi.setDeclaringNodeType(ntd.getName());
                ndi.setMandatory(false);
                ndi.setOnParentVersion(OnParentVersionAction.COPY);
                ndi.setProtected(false);
                ndi.setDefaultPrimaryType(null);
                ndi.setRequiredPrimaryTypes(new QName[]{QName.NT_BASE});

                nextToken();
                doChildNodeDefinition(ndi, ntd);
                nodeDefinitions.add(ndi);
            }
        }

        if (propertyDefinitions.size() > 0) {
            ntd.setPropertyDefs((PropDef[]) propertyDefinitions.toArray(new PropDef[0]));
        }

        if (nodeDefinitions.size() > 0) {
            ntd.setChildNodeDefs((NodeDef[]) nodeDefinitions.toArray(new NodeDef[0]));
        }
    }

    /**
     * processes the property definition
     *
     * @param pdi
     * @param ntd
     * @throws ParseException
     */
    private void doPropertyDefinition(PropDefImpl pdi, NodeTypeDef ntd)
            throws ParseException {
        if (currentToken.equals("*")) {
            pdi.setName(ItemDef.ANY_NAME);
        } else {
            pdi.setName(toQName(currentToken));
        }
        nextToken();
        doPropertyType(pdi);
        doPropertyDefaultValue(pdi);
        doPropertyAttributes(pdi, ntd);
        doPropertyValueConstraints(pdi);
    }

    /**
     * processes the property type
     *
     * @param pdi
     * @throws ParseException
     */
    private void doPropertyType(PropDefImpl pdi) throws ParseException {
        if (!currentTokenEquals(Lexer.BEGIN_TYPE)) {
            return;
        }
        nextToken();
        if (currentTokenEquals(Lexer.STRING)) {
            pdi.setRequiredType(PropertyType.STRING);
        } else if (currentTokenEquals(Lexer.BINARY)) {
            pdi.setRequiredType(PropertyType.BINARY);
        } else if (currentTokenEquals(Lexer.LONG)) {
            pdi.setRequiredType(PropertyType.LONG);
        } else if (currentTokenEquals(Lexer.DOUBLE)) {
            pdi.setRequiredType(PropertyType.DOUBLE);
        } else if (currentTokenEquals(Lexer.BOOLEAN)) {
            pdi.setRequiredType(PropertyType.BOOLEAN);
        } else if (currentTokenEquals(Lexer.DATE)) {
            pdi.setRequiredType(PropertyType.DATE);
        } else if (currentTokenEquals(Lexer.NAME)) {
            pdi.setRequiredType(PropertyType.NAME);
        } else if (currentTokenEquals(Lexer.PATH)) {
            pdi.setRequiredType(PropertyType.PATH);
        } else if (currentTokenEquals(Lexer.REFERENCE)) {
            pdi.setRequiredType(PropertyType.REFERENCE);
        } else if (currentTokenEquals(Lexer.UNDEFINED)) {
            pdi.setRequiredType(PropertyType.UNDEFINED);
        } else {
            lexer.fail("Unkown property type '" + currentToken + "' specified");
        }
        nextToken();
        if (!currentTokenEquals(Lexer.END_TYPE)) {
            lexer.fail("Missing '" + Lexer.END_TYPE + "' delimiter for end of property type");
        }
        nextToken();
    }

    /**
     * processes the property attributes
     *
     * @param pdi
     * @param ntd
     * @throws ParseException
     */
    private void doPropertyAttributes(PropDefImpl pdi, NodeTypeDef ntd) throws ParseException {
        while (currentTokenEquals(Lexer.ATTRIBUTE)) {
            if (currentTokenEquals(Lexer.PRIMARY)) {
                if (ntd.getPrimaryItemName() != null) {
                    String name = null;
                    try {
                        name = nsMapping.getJCRName(ntd.getName());
                    } catch (NoPrefixDeclaredException e) {
                        // Should never happen, checked earlier
                    }
                    lexer.fail("More than one primary item specified in node type '" + name + "'");
                }
                ntd.setPrimaryItemName(pdi.getName());
            } else if (currentTokenEquals(Lexer.AUTOCREATED)) {
                pdi.setAutoCreated(true);
            } else if (currentTokenEquals(Lexer.MANDATORY)) {
                pdi.setMandatory(true);
            } else if (currentTokenEquals(Lexer.PROTECTED)) {
                pdi.setProtected(true);
            } else if (currentTokenEquals(Lexer.MULTIPLE)) {
                pdi.setMultiple(true);
            } else if (currentTokenEquals(Lexer.COPY)) {
                pdi.setOnParentVersion(OnParentVersionAction.COPY);
            } else if (currentTokenEquals(Lexer.VERSION)) {
                pdi.setOnParentVersion(OnParentVersionAction.VERSION);
            } else if (currentTokenEquals(Lexer.INITIALIZE)) {
                pdi.setOnParentVersion(OnParentVersionAction.INITIALIZE);
            } else if (currentTokenEquals(Lexer.COMPUTE)) {
                pdi.setOnParentVersion(OnParentVersionAction.COMPUTE);
            } else if (currentTokenEquals(Lexer.IGNORE)) {
                pdi.setOnParentVersion(OnParentVersionAction.IGNORE);
            } else if (currentTokenEquals(Lexer.ABORT)) {
                pdi.setOnParentVersion(OnParentVersionAction.ABORT);
            }
            nextToken();
        }
    }

    /**
     * processes the property default values
     *
     * @param pdi
     * @throws ParseException
     */
    private void doPropertyDefaultValue(PropDefImpl pdi) throws ParseException {
        if (!currentTokenEquals(Lexer.DEFAULT)) {
            return;
        }
        List defaultValues = new ArrayList();
        do {
            nextToken();
            InternalValue value = null;
            try {
                value = InternalValue.create(ValueHelper.convert(
                        currentToken, pdi.getRequiredType(),
                        ValueFactoryImpl.getInstance()), nsMapping);
            } catch (ValueFormatException e) {
                lexer.fail("'" + currentToken + "' is not a valid string representation of a value of type " + pdi.getRequiredType());
            } catch (RepositoryException e) {
                lexer.fail("An error occured during value conversion of '" + currentToken + "'");
            }
            defaultValues.add(value);
            nextToken();
        } while (currentTokenEquals(Lexer.LIST_DELIMITER));
        pdi.setDefaultValues((InternalValue[]) defaultValues.toArray(new InternalValue[0]));
    }

    /**
     * processes the property value constraints
     *
     * @param pdi
     * @throws ParseException
     */
    private void doPropertyValueConstraints(PropDefImpl pdi) throws ParseException {
        if (!currentTokenEquals(Lexer.CONSTRAINT)) {
            return;
        }
        List constraints = new ArrayList();
        do {
            nextToken();
            ValueConstraint constraint = null;
            try {
                constraint = ValueConstraint.create(pdi.getRequiredType(), currentToken, nsMapping);
            } catch (InvalidConstraintException e) {
                lexer.fail("'" + currentToken + "' is not a valid constraint expression for a value of type " + pdi.getRequiredType());
            }
            constraints.add(constraint);
            nextToken();
        } while (currentTokenEquals(Lexer.LIST_DELIMITER));
        pdi.setValueConstraints((ValueConstraint[]) constraints.toArray(new ValueConstraint[0]));
    }

    /**
     * processes the childnode definition
     *
     * @param ndi
     * @param ntd
     * @throws ParseException
     */
    private void doChildNodeDefinition(NodeDefImpl ndi, NodeTypeDef ntd)
            throws ParseException {
        if (currentTokenEquals('*')) {
            ndi.setName(ItemDef.ANY_NAME);
        } else {
            ndi.setName(toQName(currentToken));
        }
        nextToken();
        doChildNodeRequiredTypes(ndi);
        doChildNodeDefaultType(ndi);
        doChildNodeAttributes(ndi, ntd);
    }

    /**
     * processes the childnode required types
     *
     * @param ndi
     * @throws ParseException
     */
    private void doChildNodeRequiredTypes(NodeDefImpl ndi) throws ParseException {
        if (!currentTokenEquals(Lexer.BEGIN_TYPE)) {
            return;
        }
        List types = new ArrayList();
        do {
            nextToken();
            types.add(toQName(currentToken));
            nextToken();
        } while (currentTokenEquals(Lexer.LIST_DELIMITER));
        ndi.setRequiredPrimaryTypes((QName[]) types.toArray(new QName[0]));
        nextToken();
    }

    /**
     * processes the childnode default types
     *
     * @param ndi
     * @throws ParseException
     */
    private void doChildNodeDefaultType(NodeDefImpl ndi) throws ParseException {
        if (!currentTokenEquals(Lexer.DEFAULT)) {
            return;
        }
        nextToken();
        ndi.setDefaultPrimaryType(toQName(currentToken));
        nextToken();
    }

    /**
     * processes the childnode attributes
     *
     * @param ndi
     * @param ntd
     * @throws ParseException
     */
    private void doChildNodeAttributes(NodeDefImpl ndi, NodeTypeDef ntd) throws ParseException {
        while (currentTokenEquals(Lexer.ATTRIBUTE)) {
            if (currentTokenEquals(Lexer.PRIMARY)) {
                if (ntd.getPrimaryItemName() != null) {
                    String name = null;
                    try {
                        name = nsMapping.getJCRName(ntd.getName());
                    } catch (NoPrefixDeclaredException e) {
                        // Should never happen, checked earlier
                    }
                    lexer.fail("More than one primary item specified in node type '" + name + "'");
                }
                ntd.setPrimaryItemName(ndi.getName());
            } else if (currentTokenEquals(Lexer.AUTOCREATED)) {
                ndi.setAutoCreated(true);
            } else if (currentTokenEquals(Lexer.MANDATORY)) {
                ndi.setMandatory(true);
            } else if (currentTokenEquals(Lexer.PROTECTED)) {
                ndi.setProtected(true);
            } else if (currentTokenEquals(Lexer.MULTIPLE)) {
                ndi.setAllowsSameNameSiblings(true);
            } else if (currentTokenEquals(Lexer.COPY)) {
                ndi.setOnParentVersion(OnParentVersionAction.COPY);
            } else if (currentTokenEquals(Lexer.VERSION)) {
                ndi.setOnParentVersion(OnParentVersionAction.VERSION);
            } else if (currentTokenEquals(Lexer.INITIALIZE)) {
                ndi.setOnParentVersion(OnParentVersionAction.INITIALIZE);
            } else if (currentTokenEquals(Lexer.COMPUTE)) {
                ndi.setOnParentVersion(OnParentVersionAction.COMPUTE);
            } else if (currentTokenEquals(Lexer.IGNORE)) {
                ndi.setOnParentVersion(OnParentVersionAction.IGNORE);
            } else if (currentTokenEquals(Lexer.ABORT)) {
                ndi.setOnParentVersion(OnParentVersionAction.ABORT);
            }
            nextToken();
        }
    }

    /**
     * Converts the given string into a qualified name using the current
     * namespace mapping.
     *
     * @param stringName
     * @return the qualified name
     * @throws ParseException if the conversion fails
     */
    private QName toQName(String stringName) throws ParseException {
        try {
            return NameFormat.parse(stringName, nsMapping);
        } catch (IllegalNameException e) {
            lexer.fail("Error while parsing '" + stringName + "'", e);
            return null;
        } catch (UnknownPrefixException e) {
            lexer.fail("Error while parsing '" + stringName + "'", e);
            return null;
        }
    }

    /**
     * Gets the next token from the underlying lexer.
     *
     * @see Lexer#getNextToken()
     * @throws ParseException if the lexer fails to get the next token.
     */
    private void nextToken() throws ParseException {
        currentToken = lexer.getNextToken();
    }

    /**
     * Checks if the {@link #currentToken} is semantically equal to the given
     * argument.
     *
     * @param s the tokens to compare with
     * @return <code>true</code> if equals; <code>false</code> otherwise.
     */
    private boolean currentTokenEquals(String[] s) {
        for (int i = 0; i < s.length; i++) {
            if (currentToken.equals(s[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the {@link #currentToken} is semantically equal to the given
     * argument.
     *
     * @param c the tokens to compare with
     * @return <code>true</code> if equals; <code>false</code> otherwise.
     */
    private boolean currentTokenEquals(char c) {
        return currentToken.length() == 1 && currentToken.charAt(0) == c;
    }

    /**
     * Checks if the {@link #currentToken} is semantically equal to the given
     * argument.
     *
     * @param s the tokens to compare with
     * @return <code>true</code> if equals; <code>false</code> otherwise.
     */
    private boolean currentTokenEquals(String s) {
        return currentToken.equals(s);
    }

}
