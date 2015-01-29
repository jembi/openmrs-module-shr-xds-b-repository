package org.openmrs.module.xdsbrepository.spring.servlet;

import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import org.openmrs.module.web.ModuleServlet;
import org.springframework.ws.transport.http.MessageDispatcherServlet;

/**
 * A SOAP message dispatcher servlet
 * HACK: This servlet is used to overcome an issue related to missing
 * interpretation of init-params in the servlet configuration context 
 */
public class ModuleMessageDispatcherServlet extends MessageDispatcherServlet {

	/**
     * 
     */
    private static final long serialVersionUID = 1L;

	/**
	 * Override initialize
	 * @see javax.servlet.GenericServlet#init(javax.servlet.ServletConfig)
	 */
	@Override
    public void init(ServletConfig config) throws ServletException {
	    // TODO Auto-generated method stub
		super.setContextConfigLocation("classpath:xds-servlet.xml");
		super.setTransformWsdlLocations(false);
	    super.init(new SpringFakeConfig(config.getServletName(), config.getServletContext()));
    }

	/**
	 * Fake spring config
	 */
	public static class SpringFakeConfig extends ModuleServlet.SimpleServletConfig
	{

		/**
		 * Creates an instance of the fake spring configuration
		 */
		public SpringFakeConfig(String name, ServletContext servletContext) {
	        super(name, servletContext);
        }


		/**
		 * Return an empty enumeration
		 * @see org.openmrs.module.web.ModuleServlet.SimpleServletConfig#getInitParameterNames()
		 */
		@Override
		public Enumeration getInitParameterNames() {
			return new Enumeration<Object>() {
		
				@Override
		        public boolean hasMoreElements() {
		            // TODO Auto-generated method stub
		            return false;
		        }
		
				@Override
		        public Object nextElement() {
		            // TODO Auto-generated method stub
		            return null;
		        }
				
				
			};
		}
		
	}

	

}
