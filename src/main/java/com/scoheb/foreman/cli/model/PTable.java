package com.scoheb.foreman.cli.model;

/**
 * Created by shebert on 05/01/17.
 */
public class PTable {
    public String name;

    @Override
    public String toString() {
        return "PTable{" +
                "name='" + name + '\'' +
                ", id=" + id +
                '}';
    }

    public int id;
}
