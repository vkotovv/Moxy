package moxy.compiler;

import com.google.testing.compile.Compilation;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.JavaFileObjects.forSourceLines;

@RunWith(Parameterized.class)
public class ViewStateNegativeTest extends CompilerTest {

    @Parameterized.Parameter
    public String viewClassName;

    @Parameterized.Parameters(name = "{0}")
    public static String[] data() {
        return new String[]{
                "view.NoMethodStrategyView",
                "view.NonVoidMethodView"
        };
    }

    @Test
    public void test() throws Exception {
        JavaFileObject presenter = createDummyPresenter(viewClassName);

        Compilation presenterCompilation = compileSourcesWithProcessor(presenter);

        assertThat(presenterCompilation).failed();
    }

    private JavaFileObject createDummyPresenter(String viewClass) {
        return forSourceLines("presenter.DummyPresenter",
                "package presenter;",
                "import moxy.InjectViewState;",
                "import moxy.MvpPresenter;",
                "@InjectViewState",
                "public class DummyPresenter extends MvpPresenter<" + viewClass + "> {}"
        );
    }
}
