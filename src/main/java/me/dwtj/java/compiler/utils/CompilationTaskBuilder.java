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
package me.dwtj.java.compiler.utils;

import com.sun.source.tree.CompilationUnitTree;
import me.dwtj.java.compiler.utils.proc.CompilationUnitsProcessor;
import me.dwtj.java.compiler.utils.proc.UniversalProcessor;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.convert.DefaultListDelimiterHandler;
import org.apache.commons.configuration2.convert.ListDelimiterHandler;
import org.apache.commons.configuration2.ex.ConfigurationException;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.tools.DiagnosticListener;
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

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static javax.tools.JavaFileObject.Kind.SOURCE;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static javax.tools.StandardLocation.CLASS_PATH;
import static javax.tools.StandardLocation.SOURCE_OUTPUT;
import static javax.tools.StandardLocation.SOURCE_PATH;
import static javax.tools.ToolProvider.getSystemJavaCompiler;
import static me.dwtj.java.compiler.utils.CompilationTaskBuilder.StandardJavaFileManagerConfig.makeConfig;

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
// TODO: Support passing in names of classes to `compiler.getTask()`.
// TODO: Consider allowing dependency injection in the form of a JavaCompiler
final public class CompilationTaskBuilder {

    private CompilationTaskBuilder() { }

    public static CompilationTaskBuilder newBuilder() {
        return new CompilationTaskBuilder();
    }

    /**
     * Instantiates a new builder using information in the given {@link Configuration Apache
     * Commons Configuration}. The returned builder is initialized by inspecting this configuration
     * for known properties. The values of these known properties are passed to the builder. These
     * known properties are:
     *
     * <ul>
     *   <li><code>src</code> - A list of fully-qualified class names of the source files to be
     *       compiled. These source files will be discovered somewhere on the source path. For
     *       example, if <code>pkg.subpkg.MyCls</code> is in this list, then the source path will be
     *       searched for the file <code>pkg/subpkg/MyCls.java</code>.</li>
     *   <li>
     *     <code>source_path</code> - A list of file paths to be searched for client source files.
     *   </li>
     *   <li>
     *     <code>class_path</code> - A list of file paths to be searched for pre-compiled class
     *     files.</li>
     *   <li>
     *     <code>source_output</code>: A file path where generated source files will be placed.
     *   </li>
     *   <li>
     *     <code>class_output</code>: A file path where generated class files will be placed.
     *   </li>
     *   <li>
     *     <code>options</code> - A list of compiler options.
     *   </li>
     * </ul>
     *
     * Any properties in the given configuration which are not in this list are simply ignored.
     *
     * @param config The configuration to be used in initializing the builder.
     *
     * @return A newly instantiated and appropriately initialized builder.
     */
    public static CompilationTaskBuilder newBuilder(Configuration config) {
        final StandardJavaFileManagerConfig fileManager = makeConfig();
        fileManager.addAllToClassPath(config.getList(String.class, "class_path", emptyList())
                                            .stream()
                                            .map(File::new)
                                            .collect(toList()))
                   .addAllToSourcePath(config.getList(String.class, "source_path", emptyList())
                                             .stream()
                                             .map(File::new)
                                             .collect(toList()))
                   .setSourceOutputDir(newFileOrNullPassthrough(config.getString("source_output")))
                   .setClassOutputDir(newFileOrNullPassthrough(config.getString("class_output")));

        return newBuilder().setFileManagerConfig(fileManager)
                           .addAllClasses(config.getList(String.class, "src", emptyList()))
                           .addAllOptions(config.getList(String.class, "options", emptyList()));
    }

    /**
     * A helper method to create a configuration from the given Apache Commons Configuration
     * Properties file.
     *
     * @param configFile A valid Apache Commons Configuration Properties file.
     *
     * @return A {@link Configuration} corresponding to the given file.
     *
     * @see <a href=http://commons.apache.org/proper/commons-configuration/>
     *        Apache Commons Configuration
     *      </a>
     * @see <a href=http://commons.apache.org/proper/commons-configuration/userguide/howto_properties.html>
     *        Apache Commons Configuration: Properties Files
     *      </a>
     */
    public static PropertiesConfiguration compileProperties(File configFile) {
        Parameters paramUtils = new Parameters();
        ListDelimiterHandler delim = new DefaultListDelimiterHandler(',');
        try {
            return new FileBasedConfigurationBuilder<>(PropertiesConfiguration.class)
                    .configure(paramUtils.properties().setFile(configFile))
                    .getConfiguration();
        } catch (ConfigurationException ex) {
            throw new RuntimeException("Failed to create a configuration from file " + configFile);
        }
    }

    /**
     * Instantiates a new builder using information in the given compilation configuration file.
     * The given file is interpreted as an Apache Commons Configuration properties file.
     *
     * See {@link #newBuilder(Configuration)} for the set of known properties and their
     * interpretations.
     *
     * This method uses the {@link #compileProperties} method to interpret the given file as a
     * configuration. See that method for more information on the file format.
     *
     * @param configFile A {@link File} pointing to a compile properties file.
     *
     * @return A newly instantiated and appropriately initialized builder.
     *

     */
    public static CompilationTaskBuilder newBuilder(File configFile) {
        return newBuilder(compileProperties(configFile));
    }

    private boolean isBuilt = false;

    private List<Processor> processors = new ArrayList<>();
    private List<JavaFileObject> compilationUnits = new ArrayList<>();
    private List<String> options = new ArrayList<>();
    private List<String> classes = new ArrayList<>();
    private DiagnosticListener<? super JavaFileObject> diagnostic = null;
    private StandardJavaFileManagerConfig fileManagerConfig = new StandardJavaFileManagerConfig();

    /**
     * Builds and returns a {@link CompilationTask} which is as specified by preceding calls to
     * the builder's methods. This can only be called once. Note that this consumes/re-initializes
     * the currently-set file manager config.
     *
     * @return A {@link CompilationTask} as specified by preceeding calls to the builder's methods.
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
     * about the number of rounds in which this task will be called, nor does it guarantee the
     * ordering with which the compilation task's processors are called.
     *
     * <p>By default, a compilation task has no processors.
     *
     * @param  proc The annotation processor instance to be added to the compile task.
     * @return The receiver instance (i.e. {@code this}).
     */
    public CompilationTaskBuilder addProc(Processor proc) {
        assert proc != null;
        processors.add(proc);
        return this;
    }

    /**
     * A new {@link UniversalProcessor} instance with the given task will added to the compilation
     * task. This is just a helper for calling {@link #addProc(Processor)}, so see that method for
     * details.
     *
     * @param  task A task to be performed in each round of annotation processing; in each round
     *              this task will be invoked with the current processing and round environments.
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
     * compilation task. This is just a helper for calling {@link #addProc(Processor)}, so see that
     * method for details.
     *
     * @param  task A task to be performed one at a time on each compilation unit tree found during
     *              annotation processing.
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
     * @param  config The config to be used by this builder when {@link #build()} is called.
     * @return The receiver instance (i.e. {@code this}).
     */
    public CompilationTaskBuilder setFileManagerConfig(StandardJavaFileManagerConfig config) {
        assert config != null;
        fileManagerConfig = config;
        return this;
    }

    /**
     * @return The currently-set config instance.
     */
    public StandardJavaFileManagerConfig getFileManagerConfig() {
        assert fileManagerConfig != null;
        return fileManagerConfig;
    }

    /**
     * The source of the class indicated by the given fully-qualified class name will be compiled
     * during the compilation task. This source code for this class will be looked-up using the
     * compilation task's file manager when {@link #build} is called.
     *
     * <p>By default, a compilation task has no compilation units.
     *
     * @param  cls The fully qualified name to be found and added as a compilation unit.
     * @return The receiver instance (i.e. {@code this}).
     */
    public CompilationTaskBuilder addClass(String cls) {
        classes.add(cls);
        return this;
    }

    /**
     * A helper method for {@link #addClass(String)}
     *
     * @param  classes The fully qualified names to be found and added as compilation units.
     * @return The receiver instance (i.e. {@code this}).
     */
    public CompilationTaskBuilder addAllClasses(Iterable<String> classes) {
        classes.forEach(this::addClass);
        return this;
    }

    /**
     * Adds a single compiler option to be passed to the compilation task. Note that options will
     * be passed to the compiler in the order that they were passed to this method or
     * {@link #addAllOptions}.
     *
     * <p>By default, a compilation task has no options.
     *
     * @param  opt The option to be added/appended.
     * @return The receiver instance (i.e. {@code this}).
     */
    public CompilationTaskBuilder addOption(String opt) {
        options.add(opt);
        return this;
    }

    /**
     * A helper method for {@link #addOption(String)}
     *
     * @param  opts The sequence of options to be added/appended.
     * @return The receiver instance (i.e. {@code this}).
     */
    public CompilationTaskBuilder addAllOptions(Iterable<String> opts) {
        opts.forEach(this::addOption);
        return this;
    }

    /**
     * The given compilation unit will be compiled during the compilation task.
     *
     * <p>By default, a compilation task has no compilation units.
     *
     * @param  unit The compilation unit to be added.
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
     * @param  units The compilation units to be added.
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

    /**
     * The compilation task and its file manager will use the given diagnostic listener to report
     * their notes, warnings, errors, etc.
     *
     * @param diagnostic
     *          The diagnostic listener instance to be passed to the compilation task and its file
     *          manager when {@link #build()} is called.
     *
     * @return The receiver instance (i.e. {@code} this).
     */
    public CompilationTaskBuilder setDiagnosticListener(DiagnosticListener<? super JavaFileObject>
                                                                                       diagnostic) {
        this.diagnostic = diagnostic;
        return this;
    }

    private CompilationTask buildTask() throws IOException {
        // Configure the compiler itself and its file manager.
        JavaCompiler compiler = getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostic,
                                                                              null, null);
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
                diagnostic,
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
        diagnostic = null;
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
    public static class StandardJavaFileManagerConfig {

        private EnumMap<StandardLocation, List<File>> locations;

        /**
         * Instantiates and returns a new config which will initially have all standard locations
         * set to null.
         *
         * @return The new null config.
         */
        public static StandardJavaFileManagerConfig makeConfig() {
            return new StandardJavaFileManagerConfig();
        }

        /**
         * Instantiates and returns a new config which will initially have all standard locations
         * set as in the given file manager.
         *
         * <p>Note that this method performs a shallow copy of the given file manager's location
         * information down to the {@link File} instances, that is, the given {@link File}
         * instances will be stored here, but the data structures holding them will not be.
         *
         * @param from An existing file manager from which to copy location information.
         * @return A newly instantiated with copied location information.
         */
        public static StandardJavaFileManagerConfig makeConfig(StandardJavaFileManager from) {
            assert from != null;
            StandardJavaFileManagerConfig to = makeConfig();
            for (StandardLocation l : StandardLocation.values()) {
                Iterable<? extends File> files = from.getLocation(l);
                if (files != null) {
                    to.addAllTo(l, files);
                }
            }
            return to;
        }

        /**
         * Instantiates and returns a new config which will initially have all standard locations
         * set as in the given file manager.
         *
         * <p>Note that this method performs a shallow copy of the given file manager's location
         * information down to the {@link File} instances, that is, the given {@link File}
         * instances will be stored here, but the data structures holding them will not be.
         *
         * @param  from An existing config from which to copy location information.
         * @return A newly instantiated with copied location information.
         */
        public static StandardJavaFileManagerConfig makeConfig(StandardJavaFileManagerConfig from) {
            assert from != null;
            StandardJavaFileManagerConfig to = makeConfig();
            for (StandardLocation l : StandardLocation.values()) {
                Iterable<? extends File> files = from.locations.get(l);
                if (files != null) {
                    to.addAllTo(l, files);
                }
            }
            return to;
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
         * @param  location An enum value identifying a location (e.g. {@code CLASS_PATH}).
         * @param  file     The file/directory to be added to the location.
         * @return The receiver instance (i.e. {@code this}).
         */
        public StandardJavaFileManagerConfig addTo(StandardLocation location, File file) {
            assert location != null;
            assert file != null;
            locations.putIfAbsent(location, new ArrayList<>());
            locations.get(location).add(file);
            return this;
        }

        /**
         * Adds each of the given files for the indicated location type.
         *
         * @param  location An enum value identifying a location (e.g. {@code CLASS_PATH}).
         * @param  files    The files/directories to be added to the location.
         * @return The receiver instance (i.e. {@code this}).
         */
        public StandardJavaFileManagerConfig addAllTo(StandardLocation location,
                                                      Iterable<? extends File> files) {
            assert location != null;
            assert files != null;
            files.forEach(f -> addTo(location, f));
            return this;
        }

        /**
         * Sets the given file as the only file for the indicated location type.
         *
         * Note that unlike the various <code>add*()</code> and <code>addAll*()</code> methods, this
         * method <em>does</em> allow a <code>null</code> value to be passed-in.
         *
         * @param  location An enum value identifying a location (e.g. {@code CLASS_PATH}).
         * @param  file     The file/directory to which the indicated location will be set.
         * @return The receiver instance (i.e. {@code this}).
         */
        public StandardJavaFileManagerConfig setAs(StandardLocation location, File file) {
            assert location != null;
            locations.put(location, null);  // Always clear old location information.
            if (file != null) {
                addTo(location, file);
            }
            return this;
        }

        /**
         * The given file/directory will be searched for class files during the compilation task.
         *
         * <p>By default, there are no directories (explicitly) on the class path.
         *
         * @param  f The file/directory to be added to the class path.
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
         * @param  fs The files/directories to be added to the class path.
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
         * @param  f The file/directory to be added to the source path.
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
         * @param  fs The files/directories to be added to the source path.
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
         * @param  dir The directory into which any classes generated by compilation will be placed.
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
         * @param  dir The directory into which any sources generated by compilation will be placed.
         * @return The receiver instance (i.e. {@code this}).
         */
        public StandardJavaFileManagerConfig setSourceOutputDir(File dir) {
            setAs(SOURCE_OUTPUT, dir);
            return this;
        }

        /**
         * Configures the given file manager, and then re-initializes the config instance.
         *
         * @param  fileManager The standard file manager instance to-be-configured.
         * @return The now-configured file manager instance which was just passed in.
         *
         * @throws IOException
         *            if the config attempted to set some output location to a path which does not
         *            represent an existing directory
         */
        public StandardJavaFileManager config(StandardJavaFileManager fileManager)
                                                                throws IOException {
            assert fileManager != null;
            for (StandardLocation l : StandardLocation.values()) {
                fileManager.setLocation(l, locations.get(l));
            }
            reInit();
            return fileManager;
        }
    }


    public static final String TEMP_DIR_PREFIX = "java-compiler-utils-";


    /**
     * A helper method which returns a {@link File} handle to a writable, newly created temporary
     * directory. This directory will be prefixed by {@code TEMP_DIR_PREFIX}.This may be a useful
     * helper method for creating temporary directories for outputs generated by some compilation
     * task.
     *
     * @return A handle to the newly-created temporary directory.
     *
     * @throws IOException If an I/O error occurs or the temporary-file directory does not exist.
     */
    public static File tempDir() throws IOException {
        return Files.createTempDirectory(TEMP_DIR_PREFIX).toFile();
    }

    private static File newFileOrNullPassthrough(String s) {
        return (s == null) ? null : new File(s);
    }
}
