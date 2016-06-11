/*
 * Copyright 2016 David Johnston, Jackson Maddox
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
package me.dwtj.java.compiler.runner;

import com.sun.source.tree.CompilationUnitTree;
import me.dwtj.java.compiler.proc.CompilationUnitsProcessor;
import me.dwtj.java.compiler.proc.UniversalProcessor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static javax.tools.JavaFileObject.Kind.SOURCE;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static javax.tools.StandardLocation.CLASS_PATH;
import static javax.tools.StandardLocation.SOURCE_PATH;
import static javax.tools.ToolProvider.getSystemJavaCompiler;

/**
 * A builder for (un-called) instances of {@link JavaCompiler.CompilationTask} to simplify correct
 * configuration of the {@link JavaCompiler Java Compiler API}.
 *
 * <p>All {@link CompilationTask}s created via this API are created using a {@link JavaCompiler}
 * obtained via {@link ToolProvider#getSystemJavaCompiler()}. The builder has methods of the form
 * `set*()`, `add*()`, and `addAll*()` to let the client <em>specify</em> a {@link CompilationTask}
 * which they want to create from such a {@link JavaCompiler} instance.
 *
 * <p>Each of these methods returns the builder receiver instance on which the method was called
 * (i.e. `this`) to facilitate method-chaining.
 *
 * <p>Once the client has finished specifying the desired compilation task using these methods, then
 * {@link #build()} can be called to obtain such a compilation task. This compilation task will not
 * have been called. Note that {@link #build()} can only be called once.
 *
 * <p>A key part of the configuration of a {@link JavaCompiler} is the configuration of the
 * {@link JavaFileManager} to be used in the compilation. Any {@link JavaCompiler} which is created
 * using this API uses a {@link StandardJavaFileManager} provided via its
 * {@link JavaCompiler#getStandardFileManager} method. This file manager can be configured by
 * passing a config {@link #setFileManagerConfig(StandardJavaFileManagerConfig)}.
 *
 * <p>In the case that some relevant aspect of the compilation task is not explicitly set via
 * builder method calls, the builder has been designed to use (hopefully) sensible defaults. (For
 * example, if {@link #addProc} is never called, then by default no processors will be added to the
 * compilation task by the builder.) See the relevant method's documentation for information on
 * default behavior.
 *
 * @author dwtj, jlmaddox
 */
// TODO: Let a single builder create multiple `CompilationTask` instances.
// TODO: Use diagnostics listener.
// TODO: Support passing in names of classes to `compiler.getTask()`.
// TODO: Consider allowing dependency injection in the form of a JavaCompiler
final public class CompilationTaskBuilder {

    private CompilationTaskBuilder() { }

    public static CompilationTaskBuilder newBuilder() {
        return new CompilationTaskBuilder();
    }

    private boolean isBuilt = false;

    private List<Processor> processors = new ArrayList<>();
    private List<JavaFileObject> compilationUnits = new ArrayList<>();
    private List<String> options = new ArrayList<>();
    private List<String> classes = new ArrayList<>();
    private StandardJavaFileManagerConfig fileManagerConfig = new StandardJavaFileManagerConfig();

    /**
     * Builds and returns a {@link CompilationTask} which is as specified by preceding calls to
     * the builder's methods. This can only be called once. Note that this consumes/re-initializes
     * the currently set file manager config.
     *
     * @throws IllegalStateException
     *            If this method has been called before.
     * @throws IllegalStateException
     *            If there are no compilation units to process and/or compile.
     * @throws IOException
     *            If a class or source output location <em>has</em> been set to some file which
     *            does not actually represent an existing directory.
     * @throws IOException
     *            If a class or source output location has <em>not</em> been set and some
     *            {@link IOException} occurs while trying to make a temporary directory to serve as
     *            this output location.
     * @throws IOException
     *            If the as-configured file manager cannot find some source file for some class
     *            to-be-compiled.
     */
    public CompilationTask build() throws IOException {
        if (isBuilt) {
            String msg = "`CompilationTaskBuilder.build()` can only be called once.";
            throw new IllegalStateException(msg);
        } else {
            CompilationTask task = buildTask();
            finish();
            return task;
        }
    }

    /**
     * The given {@link Processor annotation processor} instance will be used during the compilation
     * task.
     *
     * <p><em>Warning:</em> Like the {@link Processor} interface, this interface makes no guarantees
     * about the number of rounds in which this task will be called, nor does guarantee the ordering
     * with which the compilation task's processors are called.
     *
     * <p>By default, a compilation task has no processors.
     *
     * @return The receiver instance (i.e. {@code this}).
     */
    public CompilationTaskBuilder addProc(Processor proc) {
        assert proc != null;
        processors.add(proc);
        return this;
    }

    /**
     * A new {@link UniversalProcessor} instance with the given task will added to the compilation
     * task.
     *
     * <p>By default, a compilation task has no processors.
     *
     * @return The receiver instance (i.e. {@code this}).
     *
     * @see CompilationTaskBuilder#addProc(Processor)
     * @see UniversalProcessor
     */
    public CompilationTaskBuilder addProc(BiConsumer<ProcessingEnvironment, RoundEnvironment> task) {
        assert task != null;
        addProc(new UniversalProcessor(task));
        return this;
    }

    /**
     * A new {@link CompilationUnitsProcessor} with the given task will be used during the
     * compilation task.
     *
     * <p>By default, a compilation task has no processors.
     *
     * @return The receiver instance (i.e. {@code this}).
     *
     * @see CompilationTaskBuilder#addProc(Processor)
     * @see CompilationUnitTree
     * @see CompilationUnitsProcessor
     */
    public CompilationTaskBuilder addProc(Consumer<CompilationUnitTree> task) {
        assert task != null;
        addProc(new CompilationUnitsProcessor(task));
        return this;
    }

    /**
     * Set the {@link StandardJavaFileManagerConfig} instance to be used to configure the
     * compilation task's file manager.
     *
     * <p>If this method is never called with a config, then the default behavior is to use a null-
     * {@link StandardJavaFileManagerConfig}, that is, one which sets each {@link StandardLocation}
     * to {@code null}.
     *
     * <p>The compilation task will obtain and configure a file manager obtained from
     * {@link JavaCompiler#getStandardFileManager}. Note that depending on the environment,
     * some default locations may be provided to such a file manager. These defaults may be
     * overridden according to the semantics of {@link StandardJavaFileManagerConfig#config}.
     *
     * <p>Note that the config is only used <em>at build-time</em>. This means that the config
     * instance to which the builder has been set may be mutated even after this method is
     * called and these mutations will be visible at build time.
     *
     * <p>Note also that a config is "consumed" by the builder when {@link #build()} is called, or
     * to be more precise, the config will be re-initialized.
     *
     * @return The receiver instance (i.e. {@code this}).
     */
    public CompilationTaskBuilder setFileManagerConfig(StandardJavaFileManagerConfig config) {
        assert config != null;
        fileManagerConfig = config;
        return this;
    }

    /**
     * The source of the class indicated by the given fully-qualified class name will be compiled
     * during the compilation task. This source code for this class will be looked-up using the
     * compilation task's file manager when {@link #build} is called.
     *
     * <p>By default, a compilation task has no compilation units.
     *
     * @return The receiver instance (i.e. {@code this}).
     */
    public CompilationTaskBuilder addClass(String cls) {
        classes.add(cls);
        return this;
    }

    /** A helper method for {@link #addClass(String)} */
    public CompilationTaskBuilder addAllClasses(Iterable<String> cls) {
        cls.forEach(this::addClass);
        return this;
    }

    /**
     * Adds a single compiler option to be passed to the compilation task. Note that options will
     * be passed to the compiler in the order that they were passed to this method or
     * {@link #addAllOptions}.
     *
     * <p>By default, a compilation task has no options.
     *
     * @return The receiver instance (i.e. {@code this}).
     */
    public CompilationTaskBuilder addOption(String opt) {
        options.add(opt);
        return this;
    }

    /** A helper method for {@link #addOption(String)} */
    public CompilationTaskBuilder addAllOptions(Iterable<String> opts) {
        opts.forEach(this::addOption);
        return null;
    }

    /**
     * The given compilation unit will be compiled during the compilation task.
     *
     * <p>By default, a compilation task has no compilation units.
     *
     * @return The receiver instance (i.e. {@code this}).
     */
    public CompilationTaskBuilder addCompilationUnit(JavaFileObject unit) {
        assert unit != null;
        compilationUnits.add(unit);
        return this;
    }

    /**
     * All of the given compilation units will be compiled during the compilation task.
     *
     * <p>By default, a compilation task has no compilation units.
     *
     * @return The receiver instance (i.e. {@code this}).
     */
    public CompilationTaskBuilder addAllCompilationUnits(Iterable<JavaFileObject> units) {
        assert units != null;
        units.forEach(this::addCompilationUnit);
        return this;
    }

    /**
     * The compilation task will be processing-only (i.e. "-proc:only" is added as an option).
     *
     * By default, this option is not passed, i.e. both processing and compilation will occur.
     *
     * @return The receiver instance (i.e. {@code this}).
     */
    public CompilationTaskBuilder addProcOnlyOption() {
        options.add("-proc:only");
        return this;
    }

    private CompilationTask buildTask() throws IOException {
        // Configure the compiler itself and its file manager.
        JavaCompiler compiler = getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        fileManagerConfig.config(fileManager);

        // Use the file manager and the class list to get the compilation units to be compiled.
        for (String c : classes) {
            JavaFileObject srcFile = fileManager.getJavaFileForInput(SOURCE_PATH, c, SOURCE);
            if (srcFile == null) {
                throw new IOException("No such class found on the source path: " + c);
            } else {
                compilationUnits.add(srcFile);
            }
        }

        // Configure the compilation task itself.
        if (compilationUnits.isEmpty()) {
            String msg = "CompilationTaskBuilder: No compilation units have been added.";
            throw new IllegalStateException(msg);
        }
        CompilationTask task = compiler.getTask(
                null,             // TODO: Support user-defined writer.
                fileManager,
                null,             // TODO: Support user-defined diagnostics listeners.
                options,          // TODO: Support more user-defined options.
                null,             // TODO: Support user-defined classes.
                compilationUnits
        );
        task.setProcessors(processors);

        return task;
    }

    /** Sets `isBuilt` to true, and sets all fields to `null` for garbage collection. */
    private void finish() {
        isBuilt = true;
        processors = null;
        compilationUnits = null;
        options = null;
        fileManagerConfig = null;
    }


    /**
     * A helper class for configuring the locations of a {@link StandardJavaFileManager} instance.
     * It is used by calling the various `add*()`, `addAll*()`, and `set*()` methods and then
     * calling {@link #config(StandardJavaFileManager)} with the file manager to be configured.
     *
     * <p>Note that a config can only be used to configure one file manager instance, since
     * {@link #config} re-initializes the config instance.
     */
    final public static class StandardJavaFileManagerConfig {

        private EnumMap<StandardLocation, List<File>> locations;

        /** Instantiates and returns a new config which will initially set all locations to null. */
        public static StandardJavaFileManagerConfig makeNullConfig() {
            return new StandardJavaFileManagerConfig();
        }

        private StandardJavaFileManagerConfig() {
            reInit();
        }

        /**
         * Re-initializes the config as it was when it was made.
         */
        public void reInit() {
            locations = new EnumMap<>(StandardLocation.class);
        }

        /**
         * Adds the given file to the indicated location type.
         *
         * @return The receiver instance (i.e. {@code this}).
         */
        public StandardJavaFileManagerConfig addTo(StandardLocation location, File file) {
            assert file != null;
            locations.putIfAbsent(location, new ArrayList<>());
            locations.get(location).add(file);
            return this;
        }

        /**
         * Adds each of the given files for the indicated location type.
         *
         * @return The receiver instance (i.e. {@code this}).
         */
        public StandardJavaFileManagerConfig addAllTo(StandardLocation location,
                                                      Iterable<? extends File> files) {
            assert files != null;
            files.forEach(f -> addTo(location, f));
            return this;
        }

        /**
         * Sets the given file as the only file for the indicated location type.
         *
         * @return The receiver instance (i.e. {@code this}).
         */
        public StandardJavaFileManagerConfig setAs(StandardLocation location, File file) {
            assert file != null;
            locations.put(location, null);  // Clear old location information.
            addTo(location, file);
            return this;
        }

        /**
         * Sets all {@link StandardLocation}s like the given {@link StandardJavaFileManager}'s
         * current locations, clobbering any previously added or set locations. Subsequent calls
         * to other `add*()` and `set*()` will modify the configuration starting from the last call
         * to this method.
         *
         * <p>If this method is never called, then a null {@link StandardJavaFileManagerConfig} will
         * be used. (See {@link StandardJavaFileManagerConfig#makeNullConfig()}.)
         *
         * <p>Note that this method performs a shallow copy of the given file manager's location
         * information down to the {@link File} instances, that is, the given {@link File}
         * instances will be store here, but the data structures holding them will not be.
         */
        public StandardJavaFileManagerConfig setLocationsLike(StandardJavaFileManager from) {
            assert from != null;
            reInit();
            for (StandardLocation l : StandardLocation.values()) {
                Iterable<? extends File> files = from.getLocation(l);
                if (files != null) {
                    addAllTo(l, files);
                }
            }
            return this;
        }

        /**
         * The given file/directory will be searched for class files during the compilation task.
         *
         * <p>By default, there are no directories (explicitly) on the class path.
         *
         * @return The receiver instance (i.e. {@code this}).
         */
        public StandardJavaFileManagerConfig addToClassPath(File f) {
            addTo(CLASS_PATH, f);
            return this;
        }

        /**
         * All of the given files/directories will be searched for class files during the
         * compilation task.
         *
         * <p>By default, there are no directories (explicitly) on the class path.
         *
         * @return The receiver instance (i.e. {@code this}).
         */
        public StandardJavaFileManagerConfig addAllToClassPath(Iterable<File> fs) {
            addAllTo(CLASS_PATH, fs);
            return this;
        }

        /**
         * The given file/directory will be searched for source files during the compilation task.
         *
         * <p>By default, there are no directories (explicitly) on the source path.
         *
         * @return The receiver instance (i.e. {@code this}).
         */
        public StandardJavaFileManagerConfig addToSourcePath(File f) {
            addTo(SOURCE_PATH, f);
            return this;
        }

        /**
         * All of the given files/directories will be searched for source files during the
         * compilation task.
         *
         * <p>By default, there are no directories (explicitly) on the source path.
         *
         * @return The receiver instance (i.e. {@code this}).
         */
        public StandardJavaFileManagerConfig addAllToSourcePath(Iterable<File> fs) {
            addAllTo(SOURCE_PATH, fs);
            return this;
        }

        /**
         * Class files created during the compilation task will be written to this directory.
         *
         * <p>By default, newly created class files will be written to a temporary directory.
         *
         * @return The receiver instance (i.e. {@code this}).
         */
        public StandardJavaFileManagerConfig setClassOutputDir(File dir) {
            setAs(CLASS_OUTPUT, dir);
            return this;
        }

        /**
         * Source files created during the compilation task will be written to this directory.
         *
         * <p>By default, newly created source files will be written to a temporary directory.
         *
         * @return The receiver instance (i.e. {@code this}).
         */
        public StandardJavaFileManagerConfig setSourceOutputDir(File dir) {
            setAs(CLASS_OUTPUT, dir);
            return this;
        }

        /**
         * Configures the given file manager, and re-initializes the config instance.
         */
        public void config(StandardJavaFileManager fileManager) throws IOException {
            for (StandardLocation l : StandardLocation.values()) {
                fileManager.setLocation(l, locations.get(l));
            }
            reInit();
        }
    }


    public static final String TEMP_DIR_PREFIX = "java-compiler-runner-";


    /**
     * A helper method which returns a {@link File} handle to a writable, newly created temporary
     * directory. This directory will be prefixed by {@code TEMP_DIR_PREFIX}.This may be a useful
     * helper method for creating temporary directories for outputs generated by some compilation
     * task.
     */
    public static File tempDir() throws IOException {
        return Files.createTempDirectory(TEMP_DIR_PREFIX).toFile();
    }
}
