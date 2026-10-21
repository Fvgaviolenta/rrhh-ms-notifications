ALTER TABLE notifications_notificacion ADD COLUMN tipo_evento VARCHAR(60) NULL;
ALTER TABLE notifications_notificacion ADD COLUMN referencia_id CHAR(36) NULL;

UPDATE notifications_notificacion
SET tipo_evento = CASE asunto
    WHEN 'Nueva solicitud de ausencia' THEN 'ausencia.solicitada'
    WHEN 'Solicitud de ausencia aprobada' THEN 'ausencia.aprobada'
    WHEN 'Ausencia aprobada' THEN 'ausencia.aprobada'
    WHEN 'Solicitud de ausencia rechazada' THEN 'ausencia.rechazada'
    WHEN 'Contrato registrado' THEN 'contrato.creado'
    WHEN 'Contrato creado' THEN 'contrato.creado'
    WHEN 'Contrato finalizado' THEN 'contrato.finalizado'
    WHEN 'Marca editada' THEN 'asistencia.editada'
END
WHERE tipo_evento IS NULL;
