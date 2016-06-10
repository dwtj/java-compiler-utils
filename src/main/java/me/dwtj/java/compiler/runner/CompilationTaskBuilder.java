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
 * configuration of the {@link JavaCompiler} API.
 *
 * <p>The builder has methods of the form `set*()`, `add*()`, and `addAll()` in order to configure
 * the compilation task as desired. These methods all return the builder receiver instance on which
 * the method was called (i.e. `this`) to enable method-chaining. Once the appropriate methods are
 * called, then {@link #build()} can be called to obtain the specified compilation task. The
 * compilation task returned from {@link #build()} will not have been called. The {@link #build()}
 * method can only be called once.
 *
 * <p>A key part of the configuration of a {@link JavaCompiler} is the configuration of its
 * {@link JavaFileManager}. Any {@link JavaCompiler} which is configured by this API uses a
 * {@link StandardJavaFileManager} provided via its {@link JavaCompiler#getStandardFileManager}
 * method. To configure this file manager, use {@link #setFileManagerConfig}.
 *
 * <p>In the case that a some relevant aspect of the compilation task is not explicitly set via the
 * builder, the builder has been designed to use (hopefully) sensible defaults. For example, if
 * a compiler is not explicitly set, then {@link ToolProvider#getSystemJavaCompiler()} is used by
 * default. See the relevant method for information on default behavior.
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
     * Builds and returns a {@link CompilationTask} whose properties conform to prior calls of
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
            CompilationTask retVal = _build();
            _finish();
            return retVal;
        }
    }

    /**
     * The given {@link Processor annotation processor} instance will be used during the
     * compilation task.
     *
     * <p><em>Warning:</em> Like the {@link Processor} interface, this interface makes no
     * guarantees about the number of rounds in which this task will be called, nor does
     * guarantee the ordering with which the compilation task's processors are called.
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
     * <p>By default, the compilation task will use a file manager with no locations set.
     *
     * <p>Note that the config is only used <em>at build-time</em>. This means that the config
     * instance to which the builder has been set may be mutated even after it is set and these
     * effects will be visible at build time.
     *
     * <p>Note also that a config is "consumed" by the builder {@link #build()} is called, in
     * particular, the config will be re-initialized.
     *
     * @return The receiver instance (i.e. {@code this}).
     */
    public CompilationTaskBuilder setFileManagerConfig(StandardJavaFileManagerConfig config) {
        assert config != null;
        fileManagerConfig = config;
        return this;
    }

    public CompilationTaskBuilder addClass(String cls) {
        classes.add(cls);
        return this;
    }

    public CompilationTaskBuilder addAllClasses(Iterable<String> cls) {
        cls.forEach(this::addClass);
        return this;
    }

    public CompilationTaskBuilder addOption(String opt) {
        options.add(opt);
        return this;
    }

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
     * By default, this is set to `false` (i.e. both processing and compilation will occur).
     *
     * @return The receiver instance (i.e. {@code this}).
     */
    public CompilationTaskBuilder addProcOnlyOption() {
        options.add("-proc:only");
        return this;
    }

    private CompilationTask _build() throws IOException {
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
    private void _finish() {
        isBuilt = true;
        processors = null;
        compilationUnits = null;
        options = null;
        fileManagerConfig = null;
    }


    /**
     * A helper class for configuring the locations of a {@link StandardJavaFileManager} instance.
     */
    final public static class StandardJavaFileManagerConfig {

        private EnumMap<StandardLocation, List<File>> locations;

        public StandardJavaFileManagerConfig() {
            reInit();
        }

        private void reInit() {
            locations = new EnumMap<>(StandardLocation.class);
        }

        /** Adds the given file to the indicated location type. */
        public StandardJavaFileManagerConfig addTo(StandardLocation location, File file) {
            assert file != null;
            locations.putIfAbsent(location, new ArrayList<>());
            locations.get(location).add(file);
            return this;
        }

        /** Adds each of the given files for the indicated location type. */
        public StandardJavaFileManagerConfig addAllTo(StandardLocation location,
                                                      Iterable<? extends File> files) {
            assert files != null;
            files.forEach(f -> addTo(location, f));
            return this;
        }

        /** Sets the given file as the only file for the indicated location type. */
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
     * A helper method which returns a {@link File} handle to a writable, newly created
     * temporary directory. This directory will be prefixed by {@code TEMP_DIR_PREFIX}.
     *
     * <p>This may be a useful helper for creating temporary directories for outputs generated
     * by some compilation task.
     */
    public static File tempDir() throws IOException {
        return Files.createTempDirectory(TEMP_DIR_PREFIX).toFile();
    }
}
