// Copyright (c) 2015-2016 K Team. All Rights Reserved.
package org.kframework.legacykore;

import org.kframework.Collections;
import scala.collection.Set;

/**
 * Finds all patterns described by the visitor
 */
public class FindK extends AbstractFoldK<Set<K>> {
    @Override
    public Set<K> unit() {
        return Collections.<K>Set();
    }

    @Override
    public Set<K> merge(Set<K> a, Set<K> b) {
        return Collections.or(a, b);
    }
}