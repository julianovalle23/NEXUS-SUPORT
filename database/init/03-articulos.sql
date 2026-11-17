-- ===========================================================
-- NEXUS Support - Base de conocimiento (28 artículos técnicos)
-- Los artículos 27 y 28 se cargan inactivo / no verificado
-- para comprobar que no se usan como fuentes RAG.
-- ===========================================================

INSERT INTO articulo_conocimiento (titulo, problema, sintomas, causa, solucion, categoria_id, fecha_creacion, activo, verificado) VALUES
-- ---------------- Conectividad (1) ----------------
('Equipo sin acceso a internet por cable de red',
 'El equipo de escritorio no tiene acceso a internet ni a recursos de la red interna.',
 'Icono de red con aspa roja, mensaje "Cable de red desconectado", el navegador muestra "No hay conexión a internet".',
 'Cable de red dañado o desconectado, puerto del switch deshabilitado o tarjeta de red deshabilitada en el sistema.',
 '1. Verificar que el cable esté conectado en ambos extremos y que el LED del puerto encienda. 2. Probar con otro cable y otro punto de red. 3. En Configuración > Red, comprobar que el adaptador Ethernet esté habilitado. 4. Ejecutar "ipconfig /all" y confirmar que exista una IP válida. 5. Si el puerto del switch no responde, escalar a Redes con el número de punto de red.',
 1, '2026-03-02 09:00', TRUE, TRUE),

('Sin internet: IP 169.254.x.x por falla de DHCP',
 'El equipo está conectado a la red pero no navega y recibe una dirección IP autoasignada.',
 'La dirección IP comienza por 169.254, conexión "Sin acceso a internet", no se resuelven páginas web.',
 'El equipo no obtiene dirección del servidor DHCP: ámbito agotado, servicio DHCP detenido o VLAN incorrecta.',
 '1. Ejecutar "ipconfig /release" y luego "ipconfig /renew". 2. Si persiste, reiniciar el adaptador de red. 3. Verificar con Redes la disponibilidad de direcciones en el ámbito DHCP de la VLAN. 4. Como contingencia, asignar IP estática temporal de la reserva documentada. 5. Registrar la MAC del equipo en el incidente.',
 1, '2026-03-02 09:30', TRUE, TRUE),

('Navegación lenta o páginas que no cargan por fallo de DNS',
 'Hay conexión de red pero los sitios web no abren por nombre o tardan mucho en cargar.',
 'Error "DNS_PROBE_FINISHED_NXDOMAIN", "No se puede encontrar la dirección DNS del servidor", ping a una IP funciona pero a un nombre no.',
 'Servidor DNS configurado incorrectamente, caché DNS corrupta o servidor DNS interno sin respuesta.',
 '1. Ejecutar "ipconfig /flushdns". 2. Verificar con "nslookup intranet.nexus.local" qué servidor responde. 3. Confirmar que el adaptador use DNS automáticos (DHCP) y no valores manuales. 4. Probar resolución contra el DNS secundario. 5. Si el DNS interno no responde, escalar a Redes como incidente de prioridad Alta.',
 1, '2026-03-03 10:00', TRUE, TRUE),

('Wi-Fi corporativo conectado pero sin internet',
 'El portátil se conecta a la red inalámbrica corporativa pero no tiene acceso a internet.',
 'La red Wi-Fi aparece como "Conectado, sin internet", desconexiones intermitentes, baja intensidad de señal.',
 'Certificado 802.1X vencido, perfil Wi-Fi corrupto, punto de acceso saturado o controlador inalámbrico desactualizado.',
 '1. Olvidar la red NEXUS-CORP y volver a conectarse con las credenciales de dominio. 2. Verificar la fecha del certificado del equipo en certmgr.msc. 3. Actualizar el controlador de la tarjeta inalámbrica. 4. Desactivar el ahorro de energía del adaptador Wi-Fi. 5. Si varios usuarios del mismo piso reportan lo mismo, escalar a Redes por posible falla del punto de acceso.',
 1, '2026-03-03 11:00', TRUE, TRUE),

('VPN no conecta desde casa (error de autenticación o timeout)',
 'El usuario en trabajo remoto no logra establecer la conexión VPN corporativa.',
 'Mensaje "Tiempo de espera agotado", "Autenticación fallida" o la VPN conecta y se cae a los pocos segundos.',
 'Contraseña expirada, MFA no aprobado, cliente VPN desactualizado o el router doméstico bloquea el puerto UDP de la VPN.',
 '1. Confirmar que la contraseña de dominio no esté vencida. 2. Aprobar la notificación MFA en el teléfono. 3. Actualizar el cliente VPN a la versión publicada en el portal de software. 4. Probar desde otra red (por ejemplo, datos móviles) para descartar bloqueo del router. 5. Revisar el registro del cliente VPN y adjuntarlo al incidente.',
 1, '2026-03-04 08:30', TRUE, TRUE),

('Proxy mal configurado impide la navegación',
 'Los navegadores no cargan páginas externas aunque la red interna funciona.',
 'Error "ERR_PROXY_CONNECTION_FAILED", solo algunos sitios abren, aplicaciones de escritorio no se actualizan.',
 'Configuración manual de proxy antigua en el sistema o script PAC inaccesible.',
 '1. Abrir Configuración > Red > Proxy y activar "Detectar la configuración automáticamente". 2. Eliminar servidores proxy manuales heredados. 3. Ejecutar "netsh winhttp reset proxy". 4. Reiniciar el navegador y validar el acceso.',
 1, '2026-03-05 09:15', TRUE, TRUE),

-- ---------------- Hardware (2) ----------------
('Impresora de red no imprime: trabajos en cola',
 'Los documentos enviados a la impresora de red quedan en cola y no se imprimen.',
 'La cola de impresión muestra trabajos "En error" o "Pendiente", la impresora aparece desconectada.',
 'Servicio de cola de impresión bloqueado, IP de la impresora cambiada o controlador incorrecto.',
 '1. Reiniciar el servicio "Cola de impresión" (spooler). 2. Eliminar los trabajos atascados. 3. Hacer ping a la IP de la impresora y verificar que coincida con el puerto configurado. 4. Reinstalar el controlador desde el servidor de impresión. 5. Si la impresora no responde a ping, revisar cable y panel de la impresora.',
 2, '2026-03-06 10:00', TRUE, TRUE),

('Equipo no enciende o se apaga solo',
 'El computador no enciende o se apaga inesperadamente durante el uso.',
 'Sin luces al presionar el botón, apagados repentinos, ventiladores al máximo antes del apagado.',
 'Fuente de poder defectuosa, sobrecalentamiento por polvo, batería dañada o regleta sin energía.',
 '1. Verificar la toma eléctrica y probar con otro cable de poder. 2. En portátiles, retirar el cargador 30 segundos y volver a conectar. 3. Revisar temperatura con la herramienta de diagnóstico del fabricante. 4. Programar limpieza interna si hay acumulación de polvo. 5. Si no enciende, gestionar garantía o reemplazo de fuente.',
 2, '2026-03-06 11:00', TRUE, TRUE),

('Monitor sin señal o pantalla negra',
 'El monitor externo no muestra imagen aunque el equipo está encendido.',
 'Mensaje "No signal", pantalla negra, parpadeo intermitente.',
 'Cable de video suelto o dañado, entrada de video incorrecta seleccionada o controlador gráfico con fallas.',
 '1. Revisar cable HDMI/DisplayPort y la fuente de entrada del monitor. 2. Presionar Windows + P y elegir "Duplicar" o "Extender". 3. Probar con otro cable o monitor. 4. Actualizar el controlador de video. 5. Si el monitor no enciende su LED, solicitar reemplazo.',
 2, '2026-03-07 09:00', TRUE, TRUE),

('Teclado o mouse inalámbrico no responde',
 'Los periféricos inalámbricos dejan de responder o lo hacen con retraso.',
 'El cursor se congela, teclas que no escriben, retraso al escribir.',
 'Baterías descargadas, receptor USB desconectado o interferencia con otros dispositivos.',
 '1. Cambiar las baterías. 2. Conectar el receptor USB en un puerto frontal o con extensión. 3. Volver a emparejar el dispositivo. 4. Alejar el receptor de otros equipos inalámbricos. 5. Probar en otro equipo para descartar daño.',
 2, '2026-03-07 10:00', TRUE, TRUE),

('Disco duro lleno o equipo extremadamente lento',
 'El equipo funciona muy lento y muestra alertas de poco espacio.',
 'Disco C: en rojo, aplicaciones que tardan minutos en abrir, actualizaciones que fallan por falta de espacio.',
 'Archivos temporales acumulados, carpeta de descargas grande o disco mecánico con sectores dañados.',
 '1. Ejecutar "Liberador de espacio en disco" incluyendo archivos de sistema. 2. Mover archivos personales a OneDrive corporativo. 3. Revisar el estado SMART del disco. 4. Si el disco es mecánico y presenta errores, solicitar cambio a SSD.',
 2, '2026-03-08 09:00', TRUE, TRUE),

-- ---------------- Software (3) ----------------
('Actualización de Windows falla con error 0x80070002',
 'Las actualizaciones de Windows no se instalan y se revierten al reiniciar.',
 'Error 0x80070002 o 0x8024402F en Windows Update, el equipo reinicia varias veces.',
 'Componentes de Windows Update dañados o carpeta SoftwareDistribution corrupta.',
 '1. Detener los servicios wuauserv y bits. 2. Renombrar C:\Windows\SoftwareDistribution. 3. Ejecutar "sfc /scannow" y "DISM /Online /Cleanup-Image /RestoreHealth". 4. Reiniciar servicios y reintentar la actualización.',
 3, '2026-03-09 09:00', TRUE, TRUE),

('Pantalla azul (BSOD) tras instalar un controlador',
 'El equipo muestra pantalla azul después de instalar o actualizar un controlador.',
 'Pantalla azul con códigos como DRIVER_IRQL_NOT_LESS_OR_EQUAL, reinicios en bucle.',
 'Controlador incompatible o corrupto.',
 '1. Iniciar en modo seguro (Shift + Reiniciar > Solucionar problemas). 2. En el Administrador de dispositivos, revertir el controlador. 3. Desinstalar la actualización reciente. 4. Instalar el controlador certificado del fabricante. 5. Analizar el minidump si el error se repite.',
 3, '2026-03-09 10:00', TRUE, TRUE),

('Programa no se instala por falta de permisos',
 'El usuario no puede instalar una aplicación requerida para su trabajo.',
 'Mensaje "Se requieren privilegios de administrador", instalador que se cierra sin error.',
 'El usuario no tiene permisos de administrador local por política de seguridad.',
 '1. Verificar que el software esté en el catálogo aprobado. 2. Instalar desde el Portal de Empresa (Software Center). 3. Si no está en el catálogo, registrar solicitud de aprobación con Seguridad. 4. Nunca entregar credenciales de administrador al usuario.',
 3, '2026-03-10 09:00', TRUE, TRUE),

('Antivirus bloquea una aplicación legítima',
 'El antivirus corporativo pone en cuarentena o bloquea un ejecutable de trabajo.',
 'Notificación de amenaza detectada, la aplicación no abre, archivo desaparece.',
 'Falso positivo del motor heurístico del antivirus.',
 '1. Revisar en la consola del antivirus el nombre de la detección. 2. Validar el hash del archivo con el proveedor. 3. Solicitar a Seguridad una exclusión por hash, no por carpeta. 4. Restaurar el archivo desde cuarentena después de aprobada la exclusión.',
 3, '2026-03-10 10:00', TRUE, TRUE),

-- ---------------- Aplicaciones (4) ----------------
('Error de ejecución "NullPointerException" en el ERP',
 'El módulo de facturación del ERP se cierra mostrando un error de ejecución.',
 'Ventana de error con "java.lang.NullPointerException", la factura no se guarda, el error aparece al seleccionar ciertos clientes.',
 'Registros de cliente con campos obligatorios vacíos (dirección o NIT) que la aplicación no valida.',
 '1. Identificar el cliente que provoca el error según el log del ERP. 2. Completar los campos obligatorios del cliente en el maestro de terceros. 3. Reintentar la operación. 4. Reportar a Aplicaciones Corporativas el caso para agregar la validación en la siguiente versión.',
 4, '2026-03-11 09:00', TRUE, TRUE),

('Aplicación de escritorio se cierra al iniciar (falta runtime)',
 'Una aplicación corporativa de escritorio se cierra inmediatamente al abrirla.',
 'Error "No se encontró VCRUNTIME140.dll", "La aplicación no se pudo iniciar correctamente (0xc000007b)", error de ejecución al iniciar.',
 'Falta el paquete Microsoft Visual C++ Redistributable o .NET Desktop Runtime, o se instaló la arquitectura incorrecta (x86/x64).',
 '1. Revisar el Visor de eventos > Aplicación para identificar el módulo faltante. 2. Instalar Visual C++ Redistributable x86 y x64 desde el Portal de Empresa. 3. Instalar la versión de .NET Desktop Runtime requerida. 4. Reiniciar el equipo y abrir de nuevo la aplicación.',
 4, '2026-03-11 10:00', TRUE, TRUE),

('Portal web interno muestra error 500',
 'El portal web interno responde con error del servidor al ingresar a ciertos módulos.',
 'Página "500 Internal Server Error", error intermitente, otros usuarios reportan lo mismo.',
 'Excepción no controlada en el servidor de aplicaciones, pool de conexiones a base de datos agotado o despliegue reciente con errores.',
 '1. Confirmar si el error afecta a varios usuarios (incidente masivo). 2. Registrar la hora exacta y la URL. 3. Revisar los logs del servidor de aplicaciones en esa hora. 4. Si hubo despliegue reciente, coordinar rollback con Aplicaciones. 5. Reiniciar el pool de conexiones solo con autorización.',
 4, '2026-03-12 09:00', TRUE, TRUE),

('CRM no sincroniza contactos con el correo',
 'Los contactos y citas del CRM no aparecen en el calendario del usuario.',
 'Última sincronización con fecha antigua, mensaje "Token expirado".',
 'Token OAuth de integración vencido o permisos revocados en la cuenta de correo.',
 '1. En el CRM ir a Configuración > Integraciones y desconectar la cuenta. 2. Volver a conectar autorizando los permisos solicitados. 3. Forzar sincronización manual. 4. Verificar después de 15 minutos.',
 4, '2026-03-12 10:00', TRUE, TRUE),

('Aplicación muestra error de memoria "Java heap space"',
 'Al generar reportes grandes la aplicación se congela y termina con error.',
 'Mensaje "java.lang.OutOfMemoryError: Java heap space", reportes que nunca terminan.',
 'Memoria máxima asignada a la JVM insuficiente para el volumen de datos del reporte.',
 '1. Reducir el rango de fechas del reporte como contingencia. 2. Ajustar el parámetro -Xmx en el archivo de configuración de la aplicación según la guía de Aplicaciones. 3. Reiniciar la aplicación. 4. Si se repite, escalar para optimizar la consulta del reporte.',
 4, '2026-03-13 09:00', TRUE, TRUE),

-- ---------------- Correo y colaboración (5) ----------------
('Outlook no envía correos (quedan en bandeja de salida)',
 'Los mensajes se quedan en la bandeja de salida y no se envían.',
 'Mensajes en cursiva en Bandeja de salida, error 0x800CCC0F, Outlook "Desconectado".',
 'Modo sin conexión activado, adjunto mayor al límite permitido o perfil de Outlook dañado.',
 '1. Verificar que Outlook no esté en "Trabajar sin conexión". 2. Revisar que los adjuntos no superen 25 MB. 3. Reparar el perfil desde Panel de control > Correo. 4. Crear un perfil nuevo si persiste.',
 5, '2026-03-14 09:00', TRUE, TRUE),

('Buzón de correo lleno',
 'El usuario no puede recibir ni enviar correos por cuota excedida.',
 'Aviso "Su buzón está lleno", correos rebotados a remitentes externos.',
 'Cuota de buzón alcanzada por adjuntos acumulados.',
 '1. Vaciar Elementos eliminados y Correo no deseado. 2. Ordenar por tamaño y archivar adjuntos grandes en OneDrive. 3. Activar el archivo en línea si la licencia lo permite.',
 5, '2026-03-14 10:00', TRUE, TRUE),

('Sin audio o cámara en videollamadas de Teams',
 'En las reuniones de Teams los demás no escuchan o no ven al usuario.',
 'Micrófono silenciado sin motivo, cámara "en uso por otra aplicación", eco.',
 'Dispositivo de audio predeterminado incorrecto, permisos de privacidad bloqueados o cámara usada por otra aplicación.',
 '1. En Teams > Configuración > Dispositivos, seleccionar el micrófono y cámara correctos. 2. Revisar Configuración de Windows > Privacidad > Cámara/Micrófono. 3. Cerrar otras aplicaciones que usen la cámara. 4. Hacer llamada de prueba.',
 5, '2026-03-15 09:00', TRUE, TRUE),

-- ---------------- Accesos y cuentas (6) ----------------
('Cuenta de dominio bloqueada por intentos fallidos',
 'El usuario no puede iniciar sesión porque su cuenta está bloqueada.',
 'Mensaje "La cuenta de referencia está bloqueada", bloqueos repetidos después de desbloquear.',
 'Contraseña anterior guardada en el teléfono, unidades mapeadas o credenciales de Windows.',
 '1. Validar la identidad del usuario. 2. Desbloquear la cuenta en Active Directory. 3. Eliminar credenciales antiguas en el Administrador de credenciales y en el correo del teléfono. 4. Si se bloquea de nuevo, revisar el origen del bloqueo en los eventos 4740.',
 6, '2026-03-16 09:00', TRUE, TRUE),

('Restablecimiento de contraseña y MFA',
 'El usuario olvidó su contraseña o cambió de teléfono y no recibe el código MFA.',
 'No puede iniciar sesión, no llegan notificaciones de autenticación.',
 'Contraseña olvidada o método MFA registrado en un dispositivo que ya no tiene.',
 '1. Validar identidad según el procedimiento de Seguridad. 2. Usar el portal de autoservicio para restablecer la contraseña. 3. Si cambió de teléfono, eliminar métodos MFA antiguos y pedir al usuario que registre el nuevo. 4. Nunca enviar contraseñas por correo.',
 6, '2026-03-16 10:00', TRUE, TRUE),

('Acceso denegado a carpeta compartida',
 'El usuario no puede abrir una carpeta compartida de su área.',
 'Mensaje "No tiene permiso para acceder", la unidad mapeada aparece con X roja.',
 'El usuario no pertenece al grupo de seguridad de la carpeta o el ticket de permisos no fue aprobado.',
 '1. Verificar los grupos del usuario con "whoami /groups". 2. Confirmar con el dueño de la carpeta la aprobación. 3. Agregar al grupo correspondiente. 4. Cerrar sesión y volver a ingresar para aplicar el token.',
 6, '2026-03-17 09:00', TRUE, TRUE),

-- ---------------- Artículos no utilizables como fuente ----------------
('Configuración del cliente VPN antiguo (obsoleto)',
 'Procedimiento para el cliente VPN que se usaba antes de la migración.',
 'Aplica al cliente VPN versión 4.x ya retirado.',
 'El cliente fue reemplazado por la nueva plataforma VPN.',
 'Este procedimiento ya no aplica. Usar el artículo "VPN no conecta desde casa".',
 1, '2025-06-01 09:00', FALSE, TRUE),

('Borrador: falla en lector de código de barras',
 'Lectores de código de barras que no leen etiquetas en bodega.',
 'El lector emite sonido pero no escribe el código.',
 'En análisis.',
 'Documento en revisión, pendiente de verificación por Soporte N2.',
 2, '2026-10-20 09:00', TRUE, FALSE);
