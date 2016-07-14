package me.dwtj.java.compiler.utils.dagger;

import dagger.Module;
import dagger.Provides;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.inject.Singleton;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * A dagger dependency injection module which provides various core utilities available within any
 * standard Java annotation processing environment. The utilities provided come from the {@link
 * javax.lang.model Java Language Model API} and the {@link javax.annotation.processing The Java
 * Annotation Processing API}.
 *
 * @see <a href="https://docs.oracle.com/javase/8/docs/api/javax/lang/model/package-summary.html">
 *        The Java Language Model API
 *      </a>
 * @see <a href="https://docs.oracle.com/javase/8/docs/api/javax/annotation/processing/package-summary.html">
 *        The Java Annotation Processing API
 *      </a>
 *
 * @author dwtj
 */
@Module
public class StandardProcessingEnvironmentModule {

    protected final ProcessingEnvironment procEnv;

    public StandardProcessingEnvironmentModule(ProcessingEnvironment procEnv) {
        this.procEnv = procEnv;
    }

    @Provides @Singleton public ProcessingEnvironment provideProcEnv() {
        return procEnv;
    }

    @Provides @Singleton public Elements provideElementUtils() {
        return procEnv.getElementUtils();
    }

    @Provides @Singleton public Types provideTypeUtils() {
        return procEnv.getTypeUtils();
    }

    @Provides @Singleton public Messager provideMessager() {
        return procEnv.getMessager();
    }

    @Provides @Singleton public Filer provideFiler() {
        return procEnv.getFiler();
    }

}
