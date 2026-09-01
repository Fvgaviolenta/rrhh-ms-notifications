INSERT INTO notifications_notificacion (id, tenant_id, destinatario_id, canal, asunto, cuerpo, estado, creado_en) VALUES
    ('11111111-aaaa-aaaa-aaaa-aaaaaaaa0001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
     'IN_APP', 'Contrato creado', 'Se registró un nuevo contrato para Ana Pérez.', 'PENDIENTE', CURRENT_TIMESTAMP),
    ('11111111-aaaa-aaaa-aaaa-aaaaaaaa0002', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
     'EMAIL', 'Ausencia aprobada', 'La solicitud de vacaciones fue aprobada.', 'ENVIADA', CURRENT_TIMESTAMP),
    ('11111111-aaaa-aaaa-aaaa-aaaaaaaa0003', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'cccccccc-cccc-cccc-cccc-cccccccccccc',
     'IN_APP', 'Marca editada', 'Un administrador editó tu marca de asistencia.', 'PENDIENTE', CURRENT_TIMESTAMP);
