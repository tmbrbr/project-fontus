package de.tubs.cs.ias.asm_test;


public class IASReflectionProxies {
    public static Class<?> classForName(IASString str) throws ClassNotFoundException  {
        String s = str.getString();

        if(s.equals(fixup(Constants.StringQN))) { return Class.forName(fixup(Constants.TStringQN)); }
        else if(s.equals(fixup(Constants.StringBuilderQN))) { return Class.forName(fixup(Constants.TStringBuilderQN)); }
        else { return Class.forName(s); }
    }

    public static Class<?> classForName(IASString str,boolean initialize,
                                        ClassLoader loader) throws ClassNotFoundException  {
        String s = str.getString();

        if(s.equals(fixup(Constants.StringQN))) { return Class.forName(fixup(Constants.TStringQN), initialize, loader); }
        else if(s.equals(fixup(Constants.StringBuilderQN))) { return Class.forName(fixup(Constants.TStringBuilderQN), initialize, loader); }
        else { return Class.forName(s, initialize, loader); }
    }

    private static String fixup(String s) {
        return s.replace('/', '.');
    }
}
