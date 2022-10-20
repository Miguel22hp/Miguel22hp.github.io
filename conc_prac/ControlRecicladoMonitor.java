package cc.controlReciclado;

import java.util.ArrayDeque;
import java.util.Queue;

//import cc.controlReciclado.ControlRecicladoMonitor.ExcepcionNoCumpleInvariable;
import es.upm.babel.cclib.Monitor;
import es.upm.babel.cclib.Monitor.Cond;

public final class ControlRecicladoMonitor implements ControlReciclado {
	private enum Estado { LISTO, SUSTITUIBLE, SUSTITUYENDO }

	private final int MAX_P_CONTENEDOR;
	private final int MAX_P_GRUA;

	private Monitor monitor = new Monitor();
	private int peso;
	private int accediendo;
	private Estado estado;
	private Queue<Peticion> almPeticiones; // Se almacena peticiones. Usar metodo add y poll(sacar)

	// Constructor
	public ControlRecicladoMonitor (int max_p_contenedor,
			int max_p_grua) {
		MAX_P_CONTENEDOR = max_p_contenedor;
		MAX_P_GRUA = max_p_grua;
		this.peso = 0;
		this.accediendo = 0;
		this.estado = Estado.LISTO;
		this.almPeticiones = new ArrayDeque<Peticion>();
	}


	// Metodos a realizar

	// PRE: p > 0 ∧ p ≤ MAX_P_GRUA

	public void notificarPeso(int p) {
		//Comprueba la pre
		if(p <= 0 || p > MAX_P_GRUA) {
			throw new IllegalArgumentException();
		}

		monitor.enter();

		if(estado.equals(Estado.SUSTITUYENDO)) { // Si no se cumple la PRE(PRE->estado != SUSTITUYENDO)
			Peticion pet = new Peticion(p,1);
			almPeticiones.add(pet);
			pet.monitorCond.await();
			// Creas una petición, de tipo 1 porque es del metodo notificarPeso,
			// la almacenas en la cola de peticiones y pones su monitor en espera
		}

		// Seccion Critica
		if(peso + p > MAX_P_CONTENEDOR) 
			estado = Estado.SUSTITUIBLE; // Si el peso del contenedor mas el que peso a notificar(p) mayor
		// que el maximo peso posible del Contenedor, cambia el estado a SUSTITUIBLE
		else 
			estado = Estado.LISTO;       // Si el peso del contenedor mas el que peso a notificar(p) menor o igual
		// que el maximo peso posible del Contenedor, cambia el estado a LISTO

		// En la sección crítica se realiza el POST
		// Fin Seccion Critica

		desbloquear(); // llamas al metodo desbloquear para ver
		// si se puede desbloquear alguna petición 
		// almacenada en la cola
		monitor.leave();

	}

	public void incrementarPeso(int p) {

		monitor.enter();

		if(peso + p > MAX_P_CONTENEDOR || estado.equals(Estado.SUSTITUYENDO)) {// Si no se cumple la PRE(PRE->estado != SUSTITUYENDO &&
			//	  peso + p <= MAX_P_CONTENEDOR)
			Peticion pet = new Peticion(p,2);
			almPeticiones.add(pet);
			pet.monitorCond.await();
			// Creas una petición, de tipo 2 porque es del metodo incrementarPeso,
			// la almacenas en la cola de peticiones y pones su monitor en espera
		}

		// Seccion Critica

		peso += p;	 // Incrementas el peso en p
		accediendo++;// Aumentas el numero de gruas accediendo en un elemento

		// En la sección crítica se realiza el POST
		// Fin Seccion Critica

		desbloquear();// llamas al metodo desbloquear para ver
		// si se puede desbloquear alguna petición 
		// almacenada en la cola
		monitor.leave();

	}

	public void notificarSoltar() {
		
		monitor.enter();

		// Seccion Critica

		accediendo--;// Suelta una grua, por lo que se reducen las gruas accediendo

		// En la sección crítica se realiza el POST
		// Fin Seccion Critica
		desbloquear();
		monitor.leave();

	}

	public void prepararSustitucion() {
		monitor.enter();

		if(accediendo != 0 || !estado.equals(Estado.SUSTITUIBLE)) { // Si no se cumple la PRE(PRE->estado == SUSTITUIBLE &&
			//	  accediendo == 0)
			Peticion pet = new Peticion(0,3);
			almPeticiones.add(pet);
			pet.monitorCond.await();
			// Creas una petición, de tipo 3 porque es del metodo prepararSustitucion,
			// la almacenas en la cola de peticiones y pones su monitor en espera
		}

		// Seccion Critica

		estado = Estado.SUSTITUYENDO; // Se cambia el estado a SUSTITUYENDO

		// En la sección crítica se realiza el POST
		// Fin Seccion Critica

//		desbloquear();// llamas al metodo desbloquear para ver
		// si se puede desbloquear alguna petición 
		// almacenada en la cola
		monitor.leave();
	}

	public void notificarSustitucion() {

		monitor.enter();

		// Seccion Critica

		peso = 0;   		  // Se pone el peso a 0
		estado = Estado.LISTO;// Se pone el estado a LISTO
		accediendo = 0;		  // Se pone el accediendo a 0

		// Fin Seccion Critica

		desbloquear();
		monitor.leave();

	}

	// Metodos y clases auxiliares

	/*
	 * Clase Peticion
	 * 1- Diferenciamos entre los distintos tipos de peticiones que dependen de un método. Para esto se usa un identificador.
	 * 2- Cuando generas una petición la almacenas en una estrucura de datos de tipo peticion
	 * 3- Al final de cada metodo llamas a un metodo que se dedica a comprobar las peticiones, 
	 *    y las que cumple la CPRE que antes no cumplían se desbloquean.
	 *  id: el id identifica en que metodo ha sido solicitada la peticion. 
	 *  	1: NotificarPeso
	 *  	2: IncrementarPeso
	 *  	3: PrepararSustitucion
	 *  
	 *  La clase peticion tiene un atributo de tipo entero "pesoG", en el que guarda el peso pasado como parametro en ciertas llamadas
	 *  en metodos que lo usan. Tiene un id para saber desde que metodo es cada peticion generada. Todas las peticiones generan un 
	 *  Monitor condicional.
	 * */

	public class Peticion{
		public int pesoG;
		public int id; // Es un numero del 1 al 4
		public Monitor.Cond monitorCond;

		public Peticion(int pesoG, int id) {
			this.pesoG = pesoG;
			this.id = id;
			this.monitorCond = monitor.newCond();
		}
	}

	/* El metodo desbloquear recorre la cola que funciona como almacen de peticiones hasta que acaba o hasta que la recorre entera
	 * o hasta que desbloquea una peticion. Para ello, cada peticion que extrae lee su id para ver de que metodo es y comprueba si 
	 * se cumplen las condiciones de la CPRE de ese metodo. Si eso ocurre desbloquea esa peticion y acaba el metodo. Sino vuelve a
	 * añadir la peticion al final de la cola.
	 * 
	 */
	private void desbloquear() {
		int taman = almPeticiones.size();
		boolean desbloq = false; // Indica si se ha desbloqueado una peticion
		for(int i = 0; i < taman && !desbloq; i++) {
			Peticion p = almPeticiones.poll();
			int id_p = p.id;
			switch(id_p){
			case 1: // case del notificarPeso
				if(!estado.equals(Estado.SUSTITUYENDO)) { 
					p.monitorCond.signal();
					desbloq = true;
				}
				break;
			case 2: // case del incrementarPeso
				//System.out.println("CASE 2"+ estado + ", " + (peso+p.pesoG) +", " + MAX_P_CONTENEDOR);
				if(peso + p.pesoG <= MAX_P_CONTENEDOR && !estado.equals(Estado.SUSTITUYENDO)) { //TODO: FALLA
					p.monitorCond.signal();
					desbloq = true;
					//System.out.println("CASE 2 dentro if");
				}
				break;
			case 3: // case del prepararSustitucion
				//System.out.println("CASE 3" + estado + ", " + accediendo);
				if(estado.equals(Estado.SUSTITUIBLE) && accediendo==0) { //TODO: FALLA
					p.monitorCond.signal();
					desbloq = true;
					//System.out.println("CASE 3 dentro if");
				}
				break;
			}

			if(!desbloq)
				almPeticiones.add(p);
		}// fin del for
	}


}
