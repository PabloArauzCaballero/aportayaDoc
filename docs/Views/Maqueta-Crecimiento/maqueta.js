// Comportamiento propio de la maqueta de Crecimiento Financiero y Alianzas.
// Complementa Sistema-Diseno/script.js (tema, tabs, segmented, chips, toast);
// acá solo van las interacciones que este módulo agrega.

// Grid de objetivos de ahorro: selección múltiple con tope.
function toggleObjetivo(el, max){
  var grid = el.closest('.objetivos');
  var puestos = grid.querySelectorAll('.ob.on').length;
  if(!el.classList.contains('on') && max && puestos >= max){
    showToast('Elegí hasta ' + max + ' objetivos');
    return;
  }
  el.classList.toggle('on');
}

// Acordeón (el sistema define los estilos, no el comportamiento).
function toggleAcordeon(el){
  el.closest('.accordion').classList.toggle('open');
}

// Bottom sheet dentro de un teléfono de maqueta.
function abrirSheet(id){
  var s = document.getElementById(id);
  if(s) s.style.display = 'block';
}
function cerrarSheet(id){
  var s = document.getElementById(id);
  if(s) s.style.display = 'none';
}

document.addEventListener('DOMContentLoaded', function(){
  // Los caminos de "¿Qué querés hacer con tu dinero?" avisan a dónde llevan
  // cuando todavía no hay pantalla detrás.
  document.querySelectorAll('[data-aviso]').forEach(function(el){
    el.addEventListener('click', function(e){
      if(el.tagName === 'A' && el.getAttribute('href')) return;
      e.preventDefault();
      showToast(el.getAttribute('data-aviso'));
    });
  });
});
