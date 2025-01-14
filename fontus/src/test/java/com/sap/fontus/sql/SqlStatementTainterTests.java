package com.sap.fontus.sql;

import com.sap.fontus.sql.tainter.StatementTainter;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statements;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlStatementTainterTests {


    static Stream<Arguments> statementsWithExpectedOutput() throws IOException {
        return TestCaseFileParser.parseTestCases("src/test/resources/com/sap/fontus/ValidStatementsWithExpectedOutput.sql").stream();
    }

    @ParameterizedTest(name = "{index} ==> ''{0}'' should result in ''{1}''")
    @MethodSource("statementsWithExpectedOutput")
    void testTainterResult(String input, String expectedOutput) throws JSQLParserException {
        StatementTainter tainter = new StatementTainter();

        Statements stmts = CCJSqlParserUtil.parseStatements(input.trim());
        stmts.accept(tainter);

        String actualOutput = stmts.toString().trim();
        assertEquals(expectedOutput, actualOutput);
    }
}
