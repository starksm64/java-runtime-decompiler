package org.jrd.backend.data;

class TestingDummyHelper extends AbstractSourceTestClass {

    @Override
    protected String getClassName() {
        return "TestingDummy";
    }

    @Override
    protected String getPackageName() {
        return "testing.dummy";
    }

    @Override
    String getContentWithoutPackage(String nwHello) {
        return "public class " + getClassName() + " {\n" +
                "    public static void main(String[] args) throws InterruptedException {\n" +
                "        while(true) {\n" +
                "            System.out.println(\"" + nwHello + "\");\n" +
                "            Thread.sleep(1000);\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
    }

    @Override
    String getGreetings() {
        return "Hello";
    }


}
