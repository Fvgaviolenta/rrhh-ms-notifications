CREATE TABLE notifications_notificacion (
    id CHAR(36) NOT NULL PRIMARY KEY,
    tenant_id CHAR(36) NOT NULL,
    destinatario_id CHAR(36) NOT NULL,
    canal VARCHAR(30) NOT NULL,
    asunto VARCHAR(255) NOT NULL,
    cuerpo TEXT NOT NULL,
    estado VARCHAR(30) NOT NULL,
    creado_en DATETIME NOT NULL
);

CREATE INDEX idx_notificacion_tenant_destinatario ON notifications_notificacion (tenant_id, destinatario_id, creado_en);
