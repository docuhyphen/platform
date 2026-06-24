ALTER TABLE document_library
    ADD COLUMN restrict_type    boolean  NOT NULL DEFAULT false,
    ADD COLUMN restricted_type  VARCHAR(16)
        CONSTRAINT ck_doc_lib_restricted_type CHECK (restricted_type IN
            ('PDF','DOCX','DOC','XLSX','XLS','PPTX','PPT','PNG','JPG')),
    ADD COLUMN required         boolean  NOT NULL DEFAULT false;
