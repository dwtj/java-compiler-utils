/*
 * Copyright 2016 David Johnston
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.dwtj.java.compiler.proc;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.Trees;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * <p>This processor is a helper class for a running a processor in every round of a
 * compilation task and within that round, applying a single single user-defined task to the
 * round's root elements.
 *
 * <p>Instances of this class are <em>universal processors</em> (as the term is used in the docs of
 * {@link Processor})
 *
 * @see Processor
 * @see UniversalProcessor
 * @see RoundEnvironment#getRootElements
 *
 * @author dwtj
 */
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
final public class CompilationUnitsProcessor extends UniversalProcessor {

    public CompilationUnitsProcessor(Consumer<CompilationUnitTree> task) {
        super((p, r) -> getTrees(p, r).forEach(task));
    }

    private static List<CompilationUnitTree> getTrees (ProcessingEnvironment procEnv,
                                                       RoundEnvironment roundEnv) {
        Trees treeUtils = Trees.instance(procEnv);
        return roundEnv.getRootElements().stream()
                .map(root -> treeUtils.getPath(root).getCompilationUnit())
                .collect(Collectors.toList());
    }
}
