package com.sowad.nourish.notebook;

import com.sowad.nourish.notebook.command.SaveNote;
import java.util.Map;

public interface NotebookService {
  Map<String, Object> create(SaveNote command);

  Map<String, Object> update(String id, SaveNote command);

  Map<String, Object> delete(String id, Long expectedRevision);
}
