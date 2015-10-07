/* Smart Cloud/City Engine backend (SCE).
   Copyright (C) 2015 DISIT Lab http://www.disit.org - University of Florence

   This program is free software; you can redistribute it and/or
   modify it under the terms of the GNU General Public License
   as published by the Free Software Foundation; either version 2
   of the License, or (at your option) any later version.
   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.
   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the Free Software
   Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA. */

package sce;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ExpressionTree {

    private static class TreeNode {

        private final boolean leaf;   // is this a leaf? else internal
        private final String op;     // for an internal node, the operator
        private final String value;  // for a leaf, the value
        private List<TreeNode> expr; // node list

        private TreeNode(boolean leaf, String op, String value) {
            this.leaf = leaf;
            this.op = op;
            this.value = value;
            this.expr = null;
            expr = new ArrayList<>();
        }

        // for leaf nodes, show the value; for internal, the operator
        // to override Object.toString, must be public
        @Override
        public String toString() {
            return leaf ? value : op;
        }
    }

    TreeNode root = null;
    private ArrayList<String> queries; // list of queries to perform
    private String url; // url of database
    private String username; // username of database
    private String password; // password of database

    public ExpressionTree(Scanner input, ArrayList<String> queries) {
        try {
            Properties prop = new Properties();
            prop.load(this.getClass().getResourceAsStream("quartz.properties"));
            this.queries = queries;
            this.url = prop.getProperty("org.quartz.dataSource.quartzDataSource.URL");
            this.username = prop.getProperty("org.quartz.dataSource.quartzDataSource.user");
            this.password = prop.getProperty("org.quartz.dataSource.quartzDataSource.password");

            root = build(input);
        } catch (IOException ex) {
            Logger.getLogger(ExpressionTree.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    // build the expression tree
    public TreeNode build(Scanner input) {
        TreeNode node;

        String next = input.next();
        node = new TreeNode(false, next.substring(0, next.length() - 1), "");
        System.out.println(next);

        while (input.hasNext() && !input.hasNext("\\)")) {
            if (input.hasNext("AND\\(") || input.hasNext("OR\\(")) {
                node.expr.add(build(input));
            } else {
                String tmp = input.next();
                System.out.println(tmp);
                TreeNode n = new TreeNode(true, "", tmp);
                node.expr.add(n);
            }
        }
        if (input.hasNext()) {
            String tmp = input.next();
        }
        System.out.println("");
        return node;
    }

    // show the expression tree as a postfix expression
    public void showPostFix() {
        showPostFix(root);
        System.out.println();
    }

    // show the expression tree as a postfix expression (post-order traversal)
    private void showPostFix(TreeNode node) {
        if (node != null) {
            //showPostFix(node.left);
            //showPostFix(node.right);
            for (TreeNode n : node.expr) {
                showPostFix(n);
            }
            System.out.print(" ");
        }
    }

    // show the expression tree as a prefix expression
    public void showPreFix() {
        showPreFix(root);
        System.out.println();
    }

    // Show the expression tree as a prefix expression (pre-order traversal)
    private void showPreFix(TreeNode node) {
        while (node != null) {
            /*System.out.print(node + " ");
             showPreFix(node.left);
             node = node.right;*/  // Update parameter for right traversal
        }
    }

    // show the expression tree as a parenthesized infix expression
    public void showInFix() {
        showInFix(root);
        System.out.println();
    }

    // show the expression tree as a parenthesized infix expression
    private void showInFix(TreeNode node) {
        if (node != null) {
            if (!node.leaf) {
                System.out.print("( ");
            }
            int count = 0;
            for (TreeNode n : node.expr) {
                count++;
                showInFix(n);
                if (node.expr.size() != count) {
                    System.out.print(node + " ");
                }
            }
            if (!node.leaf) // Post-order position
            {
                System.out.print(") ");
            } else {
                System.out.print(node + " ");
            }
        }
    }

    // evaluate the expression and return its value
    public boolean evaluate() {
        return root == null ? false : evaluate(root);
    }

    // Evaluate the expression:  for internal nodes, this amounts
    // to a post-order traversal, in which the processing is doing
    // the actual arithmetic.  For leaf nodes, it is simply the
    // value of the node.
    private boolean evaluate(TreeNode node) {
        boolean result = false;

        // get the value of the leaf
        if (node.leaf) {
            result = query(node.value);
        } else {
            // evaluate the expression
            String operator = node.op;

            switch (operator) {
                case "AND":
                    result = true;
                    break;
                case "OR":
                    result = false;
                    break;
                default:
                    break;
            }
            for (TreeNode n : node.expr) {
                if (operator.equals("AND") && !evaluate(n)) {
                    result = false;
                    break;
                } else if (operator.equals("OR") && evaluate(n)) {
                    result = true;
                    break;
                }
            }
        }
        // return either the leaf's value or the one we just calculated.
        return result;
    }

    private boolean query(String index) {
        boolean result = false;
        Connection conn = Main.getConnection();
        try (Statement stmt = conn.createStatement()) {
            String query = this.queries.get(Integer.parseInt(index));
            ResultSet rs = stmt.executeQuery(query);
            while (rs.next()) {
                result = rs.getString("result").equals("1");
            }
        } catch (SQLException ex) {
            Logger.getLogger(ExpressionTree.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                conn.close();
            } catch (SQLException ex) {
                Logger.getLogger(ExpressionTree.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return result;
    }
}
