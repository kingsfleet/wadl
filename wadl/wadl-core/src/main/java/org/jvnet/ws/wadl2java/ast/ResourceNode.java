/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 * 
 * You can obtain a copy of the license at
 * http://www.opensource.org/licenses/cddl1.php
 * See the License for the specific language governing
 * permissions and limitations under the License.
 */

/*
 * ResourceNode.java
 *
 * Created on August 16, 2006, 12:58 PM
 *
 */

package org.jvnet.ws.wadl2java.ast;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import org.jvnet.ws.wadl.Application;
import org.jvnet.ws.wadl.Doc;
import org.jvnet.ws.wadl.Param;
import org.jvnet.ws.wadl.Resource;
import org.jvnet.ws.wadl.Resources;
import org.jvnet.ws.wadl2java.GeneratorUtil;
import java.util.ArrayList;
import java.util.List;
import org.jvnet.ws.wadl2java.ElementResolver;

/**
 * Represents a WADL resource
 * @author mh124079
 */
public class ResourceNode {
    
    private ResourceNode parentResource;
    private String className;
    private PathSegment pathSegment;
    private List<ResourceNode> childResources;
    private List<MethodNode> methods;
    private List<ResourceTypeNode> types;
    private List<Doc> doc;
    
    /**
     * Creates a new instance of ResourceNode
     * @param app the unmarshalled JAXB-generated application object
     * @param resources the unmarshalled JAXB-generated resources object
     */
    public ResourceNode(Application app, Resources resources) {
        doc = app.getDoc();
        parentResource = null;
        className = createClassNameFromBase(resources.getBase());
        pathSegment = new PathSegment(
                resources==null ? "" : resources.getBase());
        childResources = new ArrayList<ResourceNode>();
        methods = new ArrayList<MethodNode>();
        types = new ArrayList<ResourceTypeNode>();
    }

    /**
     * @return A simplified name with both the protocol and the port removed
     */
    private String createClassNameFromBase(String base) {

        try {
            URL url = new URL(base);
            String simpifiedURL =
                    url.getHost() + ((url.getPath().length() > 0 && !url.getPath().equals("/")) ? "_"
                    + url.getPath() : "");
            return GeneratorUtil.makeClassName(simpifiedURL);
        }
        catch (MalformedURLException me) {
            return GeneratorUtil.makeClassName(base);
        }
        
    }
    
    
    /**
     * Create a new instance of ResourceNode and attach it as a child of an existing
     * resource
     * @param resource the unmarshalled JAXB-generated resource object
     * @param parent the parent resource to attach the new resource to
     * @param file the URI of the WADL file that contains the resource element
     * @param idMap a map of URI reference to WADL definition element
     */
    public ResourceNode(Resource resource, ResourceNode parent, URI file, ElementResolver idMap) {
        doc = resource.getDoc();
        parentResource = parent;
        pathSegment = new PathSegment(resource, file, idMap);
        className = GeneratorUtil.makeClassName(pathSegment.getTemplate());
        childResources = new ArrayList<ResourceNode>();
        methods = new ArrayList<MethodNode>();        
        types = new ArrayList<ResourceTypeNode>();
    }

    
    /**
     * Create a new instance of ResourceNode and attach it as a child of an existing
     * resource type
     * @param resource the unmarshalled JAXB-generated resource object
     * @param parent the parent resource to attach the new resource to
     * @param file the URI of the WADL file that contains the resource element
     * @param idMap a map of URI reference to WADL definition element
     */
    public ResourceNode(Resource resource, ResourceTypeNode parent, URI file, ElementResolver idMap) {
        doc = resource.getDoc();
        parentResource = null;
        pathSegment = new PathSegment(resource, file, idMap);
        className = GeneratorUtil.makeClassName(pathSegment.getTemplate());
        childResources = new ArrayList<ResourceNode>();
        methods = new ArrayList<MethodNode>();        
        types = new ArrayList<ResourceTypeNode>();
    }
    
    /**
     * Create a new resource and add it as a child
     * @param r the unmarshalled JAXB resource element
     * @param file the URI of the WADL file that contains the resource element
     * @param idMap a map of URI reference to WADL definition element
     * @return the new resource element
     */
    public ResourceNode addChild(Resource r, URI file, ElementResolver idMap) {
        ResourceNode n = new ResourceNode(r, this, file, idMap);
        childResources.add(n);
        return n;
    }
    
    /**
     * Convenience function for generating a suitable Java class name for this WADL
     * resource
     * @return a suitable name
     */
    public String getClassName() {
        return className;
    }
    
    /**
     * Get the child resources
     * @return a list of child resources
     */
    public List<ResourceNode> getChildResources() {
        return childResources;
    }
    
    /**
     * Get the path segment for this resource
     * @return the path segment
     */
    public PathSegment getPathSegment() {
        return pathSegment;
    }
    
    /**
     * Get the method for this resource
     * @return a list of methods
     */
    public List<MethodNode> getMethods() {
        return methods;
    }
    
    /**
     * Add a new base type for this resource - adds any methods, query or
     * matrix parameters specified on the type to those specified for
     * this resource.
     * @param n the abstract resource type node to add
     */
    public void addResourceType(ResourceTypeNode n) {
        types.add(n);
        methods.addAll(n.getMethods());
        childResources.addAll(n.getResources());
        pathSegment.getQueryParameters().addAll(n.getQueryParams());
        pathSegment.getMatrixParameters().addAll(n.getMatrixParams());
    }
    
    /**
     * Get the types for this resource
     * @return a list of resource types
     */
    public List<ResourceTypeNode> getResourceTypes() {
        return types;
    }
    
    /**
     * Get the parent resource
     * @return the parent resource or null if there isn't one.
     */
    public ResourceNode getParentResource() {
        return parentResource;
    }
    
    /**
     * Get a list of path segments for this resource and its ancestors. The order of
     * segments is the reverse of the ancestor list. I.e. root resource, child of root,
     * ..., parent of resource, resource.
     * @return list of path segments
     */
    public List<PathSegment> getPathSegments() {
        List<PathSegment> list = new ArrayList<PathSegment>();
        ResourceNode n = this;
        while (n != null) {
            list.add(0, n.getPathSegment());
            n = n.getParentResource();
        }
        return list;
    }
    
    /**
     * Get a list of query parameters for this resource and its types.
     * @return list of query parameters
     */
    public List<Param> getQueryParams() {
        ArrayList<Param> completeList = new ArrayList<Param>();
        completeList.addAll(getPathSegment().getQueryParameters());
        if (getParentResource() != null)
            completeList.addAll(getParentResource().getQueryParams());
        return completeList;
    }
    
    /**
     * Get a list of header parameters for this resource and its types.
     * @return list of header parameters
     */
    public List<Param> getHeaderParams() {
        ArrayList<Param> completeList = new ArrayList<Param>();
        completeList.addAll(getPathSegment().getHeaderParameters());
        if (getParentResource() != null)
            completeList.addAll(getParentResource().getHeaderParams());
        return completeList;
    }
    
    /**
     * List of child documentation elements
     * @return documentation list, one item per language
     */
    public List<Doc> getDoc() {
        return doc;
    }
}