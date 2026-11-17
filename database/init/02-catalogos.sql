-- ===========================================================
-- NEXUS Support - Catálogos: usuarios, categorías, prioridades, estados
-- ===========================================================

INSERT INTO usuario (nombre, correo, area) VALUES
('Laura Gómez',      'laura.gomez@nexus.local',      'Soporte N1'),
('Andrés Martínez',  'andres.martinez@nexus.local',  'Soporte N1'),
('Camila Rodríguez', 'camila.rodriguez@nexus.local', 'Soporte N1'),
('Felipe Herrera',   'felipe.herrera@nexus.local',   'Soporte N2'),
('Valentina Ruiz',   'valentina.ruiz@nexus.local',   'Soporte N2'),
('Santiago Torres',  'santiago.torres@nexus.local',  'Redes y Telecomunicaciones'),
('Daniela Castro',   'daniela.castro@nexus.local',   'Redes y Telecomunicaciones'),
('Juan Pablo Ríos',  'juanpablo.rios@nexus.local',   'Aplicaciones Corporativas'),
('María José Vargas','mariajose.vargas@nexus.local', 'Aplicaciones Corporativas'),
('Sebastián Morales','sebastian.morales@nexus.local','Seguridad e Identidad');

INSERT INTO categoria (nombre, descripcion) VALUES
('Conectividad',          'Acceso a internet, red local, Wi-Fi, VPN, DNS y DHCP.'),
('Hardware',              'Equipos de cómputo, periféricos, impresoras y componentes físicos.'),
('Software',              'Sistema operativo, instalación, actualizaciones y controladores.'),
('Aplicaciones',          'Aplicaciones corporativas (ERP, CRM, portal web) y errores de ejecución.'),
('Correo y colaboración', 'Correo electrónico, calendario, videollamadas y almacenamiento compartido.'),
('Accesos y cuentas',     'Contraseñas, bloqueos de cuenta, permisos y autenticación multifactor.');

INSERT INTO prioridad (nombre, nivel, sla_horas) VALUES
('Baja',    1, 72),
('Media',   2, 24),
('Alta',    3, 8),
('Crítica', 4, 4);

INSERT INTO estado_incidente (nombre, es_final, orden) VALUES
('Abierto',     FALSE, 1),
('En progreso', FALSE, 2),
('Resuelto',    TRUE,  3),
('Cerrado',     TRUE,  4);
