package com.sowad.nourish.notebook.command;

public record SaveNote(String title, String content, Long expectedRevision) {}
