package com.nexus.support.service;

import java.util.Collection;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexus.support.domain.ArticuloConocimiento;
import com.nexus.support.dto.ArticuloDtos.*;
import com.nexus.support.exception.RecursoNoEncontradoException;
import com.nexus.support.exception.ReglaNegocioException;
import com.nexus.support.repository.ArticuloRepository;
import com.nexus.support.service.IndexacionService.ArticuloModificado;

@Service
@Transactional(readOnly = true)
public class ArticuloService {

    private final ArticuloRepository articulos;
    private final CatalogoService catalogos;
    private final ApplicationEventPublisher eventos;

    public ArticuloService(ArticuloRepository articulos, CatalogoService catalogos, ApplicationEventPublisher eventos) {
        this.articulos = articulos;
        this.catalogos = catalogos;
        this.eventos = eventos;
    }

    public List<ArticuloResponse> listar(Boolean activo, Long categoriaId) {
        return articulos.buscar(activo, categoriaId).stream().map(ArticuloResponse::de).toList();
    }

    public ArticuloResponse obtener(Long id) {
        return ArticuloResponse.de(entidad(id));
    }

    @Transactional
    public ArticuloResponse crear(ArticuloRequest req) {
        if (articulos.existsByTituloIgnoreCase(req.titulo().trim())) {
            throw new ReglaNegocioException("Ya existe un artículo con el título '" + req.titulo().trim() + "'");
        }
        ArticuloConocimiento a = new ArticuloConocimiento();
        aplicar(a, req);
        articulos.save(a);
        eventos.publishEvent(new ArticuloModificado(a.getId()));
        return ArticuloResponse.de(a);
    }

    @Transactional
    public ArticuloResponse actualizar(Long id, ArticuloRequest req) {
        ArticuloConocimiento a = entidad(id);
        if (articulos.existsByTituloIgnoreCaseAndIdNot(req.titulo().trim(), id)) {
            throw new ReglaNegocioException("Ya existe otro artículo con el título '" + req.titulo().trim() + "'");
        }
        aplicar(a, req);
        eventos.publishEvent(new ArticuloModificado(id));
        return ArticuloResponse.de(a);
    }

    /** Activa/desactiva o verifica un artículo. Un artículo inactivo deja de ser fuente del RAG. */
    @Transactional
    public ArticuloResponse cambiarEstado(Long id, EstadoArticuloRequest req) {
        ArticuloConocimiento a = entidad(id);
        a.setActivo(req.activo());
        a.setVerificado(req.verificado());
        eventos.publishEvent(new ArticuloModificado(id));
        return ArticuloResponse.de(a);
    }

    /** Todos los artículos (incluidos inactivos) para que n8n indexe o elimine vectores. */
    public List<ArticuloIndexable> indexables() {
        return articulos.buscar(null, null).stream().map(ArticuloIndexable::de).toList();
    }

    /** Filtra los ids recuperados de Qdrant dejando solo los que existen y son fuente válida. */
    public List<FuenteContexto> fuentesValidas(Collection<Long> ids) {
        return articulos.findFuentesValidas(ids).stream().map(FuenteContexto::de).toList();
    }

    private ArticuloConocimiento entidad(Long id) {
        return articulos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Artículo", id));
    }

    private void aplicar(ArticuloConocimiento a, ArticuloRequest req) {
        a.setTitulo(req.titulo().trim());
        a.setProblema(req.problema().trim());
        a.setSintomas(req.sintomas().trim());
        a.setCausa(req.causa().trim());
        a.setSolucion(req.solucion().trim());
        a.setCategoria(req.categoriaId() == null ? null : catalogos.categoria(req.categoriaId()));
        if (req.verificado() != null) {
            a.setVerificado(req.verificado());
        }
    }
}
