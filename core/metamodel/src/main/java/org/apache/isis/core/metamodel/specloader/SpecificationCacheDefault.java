/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.isis.core.metamodel.specloader;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.isis.commons.internal.collections._Lists;
import org.apache.isis.commons.internal.collections._Maps;
import org.apache.isis.commons.internal.collections._Sets;
import org.apache.isis.core.metamodel.facets.object.objectspecid.ObjectSpecIdFacet;
import org.apache.isis.core.metamodel.spec.ObjectSpecId;
import org.apache.isis.core.metamodel.spec.ObjectSpecification;

/**
 * This is populated in two parts.
 *
 * Initially the <tt>specByClassName</tt> map is populated using {@link #cache(String, ObjectSpecification)}.
 * This allows {@link #allSpecifications()} to return a list of specs.
 * Later on, {@link #init()} called which populates #classNameBySpecId.
 *
 * Attempting to call {@link #getByObjectType(ObjectSpecId)} before {@link #init() initialisation} will result in an
 * {@link IllegalStateException}.
 *
 */
class SpecificationCacheDefault {

    private final Map<String, ObjectSpecification> specByClassName = _Maps.newHashMap();
    private Map<ObjectSpecId, String> classNameBySpecId;
    private Set<String> ignoredClassNames;

    public ObjectSpecification get(final String className) {
        return specByClassName.get(className);
    }

    public void cache(final String className, final ObjectSpecification spec) {
        ensureCachesConsistent();
        specByClassName.put(className, spec);
        recache(spec);
        ensureCachesConsistent();
    }


    public void clear() {
        specByClassName.clear();
    }

    public Collection<ObjectSpecification> allSpecifications() {
        return Collections.unmodifiableCollection(specByClassName.values());
    }

    public ObjectSpecification getByObjectType(ObjectSpecId objectSpecID) {
        if (!isInitialized()) {
            throw new IllegalStateException("SpecificationCache by object type has not yet been initialized");
        }
        final String className = classNameBySpecId.get(objectSpecID);
        return className != null ? specByClassName.get(className) : null;
    }

    synchronized void init() {
        final Collection<ObjectSpecification> objectSpecifications = allSpecifications();
        final Map<ObjectSpecId, ObjectSpecification> specById = _Maps.newHashMap();
        for (final ObjectSpecification objSpec : objectSpecifications) {
            final ObjectSpecId objectSpecId = objSpec.getSpecId();
            if (objectSpecId == null) {
                continue;
            }
            specById.put(objectSpecId, objSpec);
        }

        internalInit(specById);
    }

    void internalInit(final Map<ObjectSpecId, ObjectSpecification> specById) {
        final Map<ObjectSpecId, String> classNameBySpecId = _Maps.newHashMap();
        final Map<String, ObjectSpecification> specByClassName = _Maps.newHashMap();

        for (ObjectSpecId objectSpecId : specById.keySet()) {
            final ObjectSpecification objectSpec = specById.get(objectSpecId);
            final String className = objectSpec.getCorrespondingClass().getName();
            classNameBySpecId.put(objectSpecId, className);
            specByClassName.put(className, objectSpec);
        }

        this.classNameBySpecId = classNameBySpecId;
        this.ignoredClassNames = _Sets.newHashSet();

        this.specByClassName.clear();
        this.specByClassName.putAll(specByClassName);
        ensureCachesConsistent();
    }

    public ObjectSpecification remove(String typeName) {
        ensureCachesConsistent();
        ObjectSpecification removed = specByClassName.remove(typeName);
        if(removed != null) {
            if(removed.containsDoOpFacet(ObjectSpecIdFacet.class)) {
                // umm.  It turns out that anonymous inner classes (eg org.estatio.dom.WithTitleGetter$ToString$1)
                // don't have an ObjectSpecId; hence the guard.
                ObjectSpecId specId = removed.getSpecId();
                classNameBySpecId.remove(specId);
            }
        }
        ensureCachesConsistent();
        return removed;
    }

    /**
     * @param spec
     */
    public void recache(ObjectSpecification spec) {
        if(!isInitialized()) {
            // JRebel plugin might call this before we are actually up and running;
            // just ignore.
            return;
        }
        final String className = spec.getCorrespondingClass().getName();
        if(!spec.containsDoOpFacet(ObjectSpecIdFacet.class)) {
            ignoredClassNames.add(className);
        } else {
            classNameBySpecId.put(spec.getSpecId(), className);
            ignoredClassNames.remove(className);
        }
        ensureCachesConsistent();
    }

    boolean isInitialized() {
        return classNameBySpecId != null;
    }

    private void ensureCachesConsistent() {
        // DISABLED...
        if(false &&
           isInitialized() && (specByClassName.size() != (classNameBySpecId.size() + ignoredClassNames.size())) ) {

            final List<String> inSpecByClassNameNotIn = _Lists.newArrayList(specByClassName.keySet());
            inSpecByClassNameNotIn.removeAll(classNameBySpecId.values());
            inSpecByClassNameNotIn.removeAll(ignoredClassNames);

            final List<String> inClassNameBySpecIdNotIn = _Lists.newArrayList(classNameBySpecId.values());
            inClassNameBySpecIdNotIn.removeAll(specByClassName.keySet());

            throw new IllegalStateException(
                    String.format("Caches specByClassName and (classNameBySpecId + ignoredClassNames) differ in size! %d vs %d",
                            specByClassName.size(), classNameBySpecId.size()));
        }
    }


}
