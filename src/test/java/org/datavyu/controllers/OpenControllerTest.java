/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.datavyu.controllers;

import static org.testng.Assert.assertEquals;

import org.apache.logging.log4j.LogManager;
import org.datavyu.models.db.Cell;
import org.datavyu.models.db.DataStore;
import org.datavyu.models.db.Variable;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


/**
 * Tests for opening Datavyu project and CSV files. Datavyu's text and nominal columns open as annotation columns with
 * one code, {@code var}.
 */
public class OpenControllerTest {

    // The location of the test files.
    private static final String TEST_FOLDER = System.getProperty("testPath");

    @BeforeClass
    public void spinUp() {
        LogManager.getLogger();
    }

    @AfterClass
    public void spinDown() {
    }

    @Test
    public void testLoadCSV() {
        File demoFile = new File(TEST_FOLDER, "IO/simple1.csv");
        OpenController openc = new OpenController();
        openc.openDataStore(demoFile);

        DataStore ds = openc.getDataStore();
        List<Variable> vars = ds.getAllVariables();
        assertEquals(vars.size(), 1);
        assertEquals(vars.get(0).getName(), "TestColumn");
        assertEquals(codes(vars.get(0)), List.of("var"));
        assertEquals(vars.get(0).isHidden(), false);

        List<Cell> cells = vars.get(0).getCells();
        assertEquals(cells.size(), 1);
        assertEquals(cells.get(0).getOnsetString(), "00:01:00:000");
        assertEquals(cells.get(0).getOffsetString(), "00:02:00:000");
        assertEquals(cells.get(0).getValueAsString(), "(This is a test cell.)");
    }

    @Test
    public void testLoadOPF() {
        File demoFile = new File(TEST_FOLDER, "IO/simple2.opf");
        OpenController openc = new OpenController();
        openc.openProject(demoFile);

        DataStore ds = openc.getDataStore();
        List<Variable> vars = ds.getAllVariables();
        assertEquals(vars.size(), 1);
        assertEquals(vars.get(0).getName(), "TestColumn");
        assertEquals(codes(vars.get(0)), List.of("var"));
        assertEquals(vars.get(0).isHidden(), false);

        List<Cell> cells = vars.get(0).getCells();
        assertEquals(cells.size(), 1);
        assertEquals(cells.get(0).getOnsetString(), "00:01:00:000");
        assertEquals(cells.get(0).getOffsetString(), "00:02:00:000");
        assertEquals(cells.get(0).getValueAsString(), "(This is a test cell.)");
    }

    @Test
    public void testLoadOPF2() {
        File demoFile = new File(TEST_FOLDER, "IO/simple3.opf");
        OpenController openc = new OpenController();
        openc.openProject(demoFile);

        DataStore ds = openc.getDataStore();
        List<Variable> vars = ds.getAllVariables();
        assertEquals(vars.size(), 4);
        
        // Sort the columns by name to match up the order with what we expect.
        Collections.sort(vars, 
                new Comparator<Variable>(){
                    @Override
                    public int compare(Variable v1, Variable v2){
                        return (v1.getName().compareTo(v2.getName()));
                    }
                }
        );
        
        Variable var = vars.get(0);
        assertEquals(var.getName(), "hiddenColumn");
        assertEquals(codes(var), List.of("var"));
        assertEquals(var.isHidden(), true);
        List<Cell> cells = var.getCells();
        assertEquals(cells.size(), 0);
        
        var = vars.get(1);
        assertEquals(var.getName(), "testColumn");
        assertEquals(codes(var), List.of("var"));
        assertEquals(var.isHidden(), false);
        cells = var.getCells();
        assertEquals(cells.size(), 1);
        assertEquals(cells.get(0).getValueAsString(), "(cellA)");

        var = vars.get(2);
        assertEquals(var.getName(), "testColumn2");
        assertEquals(codes(var), List.of("var"));
        assertEquals(var.isHidden(), false);
        cells = var.getCells();
        assertEquals(cells.size(), 1);
        assertEquals(cells.get(0).getValueAsString(), "(cellB)");

        var = vars.get(3);
        assertEquals(var.getName(), "testColumn3");
        assertEquals(codes(var), List.of("code01"));
        assertEquals(var.isHidden(), false);
        cells = var.getCells();
        assertEquals(cells.size(), 1);
        assertEquals(cells.get(0).getValueAsString(), "(cellC)");
    }

    @Test
    public void testLoadOPF3() {
        File demoFile = new File(TEST_FOLDER, "IO/simple4.opf");
        OpenController openc = new OpenController();
        openc.openProject(demoFile);

        DataStore ds = openc.getDataStore();
        List<Variable> vars = ds.getAllVariables();
        assertEquals(vars.size(), 1);
        assertEquals(vars.get(0).getName(), "blah");

        List<Cell> cells = vars.get(0).getCells();
        assertEquals(cells.size(), 1);
    }

    private static List<String> codes(final Variable variable) {
        return variable.getRootNode().childArguments.stream().map(code -> code.name).toList();
    }
}
