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
package me.dwtj.java.compiler.utils.proc;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * <p>This processor is a helper class for running single user-defined task in every round of a
 * Java compilation task. In any round, the user's task will always be passed the current
 * {@link ProcessingEnvironment} and {@link RoundEnvironment}.
 *
 * <p>Instances of this class are <em>universal processors</em> (as the term is used in the docs of
 * {@link Processor}): An instance will claim all annotation types ({@code "*"}), and it will be run
 * even if all root elements of a given round have no annotations on them.
 *
 * @see Processor
 * @author dwtj on 2/25/16.
 */
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class UniversalProcessor extends AbstractProcessor {

    private final BiConsumer<ProcessingEnvironment, RoundEnvironment> task;

    public UniversalProcessor(BiConsumer<ProcessingEnvironment, RoundEnvironment> task) {
        this.task = task;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        task.accept(processingEnv, roundEnv);
        return false;
    }
}

