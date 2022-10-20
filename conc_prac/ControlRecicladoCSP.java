package cc.controlReciclado;

import java.util.ArrayDeque;
import java.util.Queue;

import org.jcsp.lang.*;

public class ControlRecicladoCSP implements ControlReciclado, CSProcess {
	private enum Estado { LISTO, SUSTITUIBLE, SUSTITUYENDO }

	private final int MAX_P_CONTENEDOR;
	private final int MAX_P_GRUA;
	private int peso;
	private int accediendo;
	private Estado estado;
	private static final int NOTIFICARPESO = 0;//grua
	private static final int INCREMENTARPESO = 1;//grua
	private static final int NOTIFICARSOLTAR = 2;//grua
	private static final int PREPARARSUSTITUCION= 3;//contenedor
	private static final int NOTIFICARSUSTITUCION = 4;//contenedor
	//CANALES.
	private final Any2OneChannel nPeso ; // Este canal es el que usas en metodos que se comunican con 
	private final Any2OneChannel iPeso ; // Este canal es el que usas en metodos que se comunican con 
	private final Any2OneChannel nSoltar ; // Este canal es el que usas en metodos que se comunican con 
	private final Any2OneChannel pSustitucion ; // Este canal es el que usas en metodos que se comunican con 
	private final Any2OneChannel nSustitucion ; // Este canal es el que usas en metodos que se comunican con 
	


	public ControlRecicladoCSP (int max_p_contenedor, int max_p_grua) {
		MAX_P_CONTENEDOR = max_p_contenedor;
		MAX_P_GRUA = max_p_grua;
		this.peso = 0;
		this.accediendo = 0;
		this.estado = Estado.LISTO;
		nPeso = Channel.any2one();
		iPeso = Channel.any2one();
		nSoltar = Channel.any2one();
		pSustitucion = Channel.any2one();
		nSustitucion = Channel.any2one();
		new ProcessManager(this).start();
		
	}

	public void notificarPeso(int p) {
		//Comprueba la pre
		if(p <= 0 || p > MAX_P_GRUA) {
			throw new IllegalArgumentException();
		}
		
		One2OneChannel sincro = Channel.one2one(); // Por este canal recibes la respuesta del servidor
		Object[] peticion = {NOTIFICARPESO,sincro, p}; // Creamos una peticion donde guardamos:
		// peticion[0] = tipo de peticion
		// peticion[1] = canal por donde devuelves el dato
		// peticion[2] = p que es lo que debes notificar
		nPeso.out().write(peticion);
		sincro.in().read();
	}

	public void incrementarPeso(int p) {
		One2OneChannel sincro = Channel.one2one(); // Por este canal recibes la respuesta del servidor
		Object[] peticion = {INCREMENTARPESO,sincro, p}; // Creamos una peticion donde guardamos:
		// peticion[0] = tipo de peticion
		// peticion[1] = canal por donde devuelves el dato
		// peticion[2] = p que es lo que debes notificar
		iPeso.out().write(peticion);
		sincro.in().read();
	}

	public void notificarSoltar() {
		One2OneChannel sincro = Channel.one2one(); // Por este canal recibes la respuesta del servidor
		Object[] peticion = {NOTIFICARSOLTAR,sincro}; // Creamos una peticion donde guardamos:
		// peticion[0] = tipo de peticion
		// peticion[1] = canal por donde devuelves el dato
		nSoltar.out().write(peticion);
		sincro.in().read();
	}

	public void prepararSustitucion() {
		One2OneChannel sincro = Channel.one2one();
		Object[] peticion = {PREPARARSUSTITUCION,sincro}; // Creamos una peticion donde guardamos:
		// peticion[0] = tipo de peticion
		// peticion[1] = canal por donde devuelves el dato
		pSustitucion.out().write(peticion);
		sincro.in().read();
	}

	public void notificarSustitucion() {
		One2OneChannel sincro = Channel.one2one(); // Por este canal recibes la respuesta del servidor
		Object[] peticion = {NOTIFICARSUSTITUCION,sincro}; // Creamos una peticion donde guardamos:
		// peticion[0] = tipo de peticion
		// peticion[1] = canal por donde devuelves el dato
		nSustitucion.out().write(peticion);
		sincro.in().read();
	}

	public void run() {
		Queue<Object[]> almPeticiones = new ArrayDeque<>();
		Object[] pet;    // Objeto donde guardaremos las peticiones que recibamos de los diferentes canales
		One2OneChannel canalRespuesta;    // Canal que utilizaremos para devolver respuestas a los clientes
		
		Guard[] entradas = { 
				nPeso.in(),
				iPeso.in(),
				nSoltar.in(),
				pSustitucion.in(),
				nSustitucion.in()
		};

		Alternative servicios = new Alternative(entradas);

		while(true) {
			boolean[] sincCond = new boolean[5];
			sincCond[NOTIFICARPESO] = !estado.equals(Estado.SUSTITUYENDO);//notificarPeso
			sincCond[INCREMENTARPESO] = !estado.equals(Estado.SUSTITUYENDO);//incrementarPeso. Comprobare aqui solo la parte que no depende de parametro?
			sincCond[NOTIFICARSOLTAR] = true;//notificarSoltar
			sincCond[PREPARARSUSTITUCION] = estado.equals(Estado.SUSTITUIBLE) && accediendo == 0;//prepararSustitucion
			sincCond[NOTIFICARSUSTITUCION] = true;//notificarSustitucion
			try {
				int selec = servicios.fairSelect(sincCond);
				switch(selec) {
				case NOTIFICARPESO:
					pet = (Object[]) nPeso.in().read();			// Obtenemos la peticion del canal de extraccion
					int pesoP = (int) pet[2];		// Sacamos el numero de productos a extraer
					canalRespuesta = ((One2OneChannel) pet[1]);		// Sacamos el canal de sincro para devolver la respuesta

					if(peso + pesoP > MAX_P_CONTENEDOR) 
						estado = Estado.SUSTITUIBLE; // Si el peso del contenedor mas el que peso a notificar(p) mayor
					// que el maximo peso posible del Contenedor, cambia el estado a SUSTITUIBLE
					else 
						estado = Estado.LISTO;       // Si el peso del contenedor mas el que peso a notificar(p) menor o igual
					// que el maximo peso posible del Contenedor, cambia el estado a LISTO
					canalRespuesta.out().write(peso);

					break;
				case INCREMENTARPESO:
					pet = (Object[]) iPeso.in().read();			// Obtenemos la peticion del canal de extraccion
					int pesoG = (int) pet[2];		// Sacamos el numero de productos a extraer
					canalRespuesta = ((One2OneChannel) pet[1]);		// Sacamos el canal de sincro para devolver la respuesta

					if(peso + pesoG <= MAX_P_CONTENEDOR) {//terminas de comprobar la CPRE
						peso += pesoG;	 // Incrementas el peso en p
						accediendo++;// Aumentas el numero de gruas accediendo en un elemento
						canalRespuesta.out().write(peso);
					}
					else {
						almPeticiones.add(pet);
					}

					break;
				case NOTIFICARSOLTAR:
					pet = (Object[]) nSoltar.in().read();			// Obtenemos la peticion del canal de extraccion
					canalRespuesta = ((One2OneChannel) pet[1]);		// Sacamos el canal de sincro para devolver la respuesta

					accediendo--;// Suelta una grua, por lo que se reducen las gruas accediendo

					canalRespuesta.out().write(peso);

					break;
				case PREPARARSUSTITUCION:
					pet = (Object[]) pSustitucion.in().read();			// Obtenemos la peticion del canal de extraccion
					canalRespuesta = ((One2OneChannel) pet[1]);		// Sacamos el canal de sincro para devolver la respuesta

					estado = Estado.SUSTITUYENDO;

					canalRespuesta.out().write(peso);

					break;
				case NOTIFICARSUSTITUCION:
					pet = (Object[]) nSustitucion.in().read();			// Obtenemos la peticion del canal de extraccion
					canalRespuesta = ((One2OneChannel) pet[1]);		// Sacamos el canal de sincro para devolver la respuesta

					peso = 0;   		  // Se pone el peso a 0
					estado = Estado.LISTO;// Se pone el estado a LISTO
					accediendo = 0;		  // Se pone el accediendo a 0

					canalRespuesta.out().write(peso);

					break;	
				}// fin del switch

				// tratamos de comprobar si las peticiones que hay almacenadas en almPeticiones se pueden resolver
				int tamalmPeticiones = almPeticiones.size();
				for(int i = 0; i < tamalmPeticiones; i++) {
					// todas las peticiones que hay aqui son del metodo incrementarPeso, porque el resto gracias al booleano de las CPRE
					// no las escoges salvo que se cumplan su CPRE, pero al depender la CPRE de esta de un valor se tiene que comprobar
					// una vez elegida
					pet = almPeticiones.poll(); // Sacas la peticion y la eliminas de la cola
					int pesoG = (int) pet[2];		// Sacamos el numero de productos a extraer
					canalRespuesta = ((One2OneChannel) pet[1]);		// Sacamos el canal de sincro para devolver la respuesta

					if(peso + pesoG <= MAX_P_CONTENEDOR) {//terminas de comprobar la CPRE
						peso += pesoG;	 // Incrementas el peso en p
						accediendo++;// Aumentas el numero de gruas accediendo en un elemento
						canalRespuesta.out().write(peso);
					}
					else {
						almPeticiones.add(pet);
					}
				}
			}catch(ProcessInterruptedException e){};

		}// fin del while
	} // fin el run
}
