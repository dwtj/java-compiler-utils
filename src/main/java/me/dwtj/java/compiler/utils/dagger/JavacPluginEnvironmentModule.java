package me.dwtj.java.compiler.utils.dagger;

import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;
import dagger.Module;
import dagger.Provides;

import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Singleton;

/**
 * A dagger dependency injection module which provides various core utilities available within an
 * Oracle javac compiler plugin or annotation processor. Because this extends the {@link
 * StandardProcessingEnvironmentModule}, this provides all of those utilities available within any
 * standard Java annotation processing environment. This extends that set of utilities with those
 * available only within Oracle's javac. The main addition here are those utilities from the
 * Compiler Tree API.
 *
 * @see <a href="https://docs.oracle.com/javase/8/docs/jdk/api/javac/tree/">Compiler Tree API</a>
 *
 * @author dwtj
 */
@Module
public class JavacPluginEnvironmentModule extends StandardProcessingEnvironmentModule {

    public JavacPluginEnvironmentModule(ProcessingEnvironment procEnv) {
        // TODO: Assert that this really is a javac plugin/proc environment.
        super(procEnv);
    }

    @Provides @Singleton public Trees provideTreeUtils() {
        return Trees.instance(procEnv);
    }

    @Provides @Singleton public JavacTask provideJavacTask() {
        return JavacTask.instance(procEnv);
    }

    @Provides @Singleton public SourcePositions provideSourcePositions(Trees treeUtils) {
        return treeUtils.getSourcePositions();
    }
}
